package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.FiltroAuditoria;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Auditoria persistente no MongoDB (RF05).
 *
 * - Grava quem fez (usuário e perfil), quando, qual operação, em qual entidade/id,
 *   os campos alterados com valores anterior e novo e a justificativa, quando exigida.
 * - Só oferece gravação de novos registros e consulta: não existe alteração nem exclusão.
 * - A consulta é restrita a ADMIN e GERENTE. Para o ADMIN, cuja finalidade é a
 *   administração do sistema e não o atendimento, os dados pessoais dos clientes
 *   (nome, telefone e endereço) aparecem mascarados.
 */
@Service
public class AuditoriaService {

    public static final String ENTIDADE_PEDIDO = "Pedido";
    public static final String ENTIDADE_CLIENTE = "Cliente";
    public static final String ENTIDADE_PRODUTO = "Produto";
    public static final String ENTIDADE_USUARIO = "Usuario";
    public static final String ENTIDADE_SEGURANCA = "Seguranca";

    public static final int TAMANHO_MAXIMO_PAGINA = 100;

    private static final Set<String> CAMPOS_DADOS_PESSOAIS = Set.of(
            "nome", "telefone", "cep", "logradouro", "numero", "complemento", "endereco");

    private final RegistroAuditoriaRepository repository;
    private final MongoTemplate mongoTemplate;
    private final UsuarioLogado usuarioLogado;
    private final Clock clock;

    public AuditoriaService(RegistroAuditoriaRepository repository, MongoTemplate mongoTemplate,
            UsuarioLogado usuarioLogado, Clock clock) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.usuarioLogado = usuarioLogado;
        this.clock = clock;
    }

    // ===================== Gravação =====================

    public RegistroAuditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
            String descricao, List<CampoAlterado> alteracoes, String justificativa) {
        RegistroAuditoria registro = new RegistroAuditoria(
                LocalDateTime.now(clock),
                usuarioLogado.getUsername(),
                usuarioLogado.getNomePerfil(),
                operacao,
                entidade,
                entidadeId,
                descricao,
                alteracoes,
                justificativa);
        return repository.save(registro);
    }

    /** Registra uma tentativa de acesso negado (sem cabeçalhos, parâmetros ou credenciais). */
    public RegistroAuditoria registrarAcessoNegado(String metodoHttp, String uri, String motivo) {
        return registrar(TipoOperacao.ACESSO_NEGADO, ENTIDADE_SEGURANCA, metodoHttp + " " + uri,
                "Tentativa de acesso bloqueada: " + motivo, new ArrayList<>(), null);
    }

    // ===================== Consulta =====================

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Page<RegistroAuditoria> consultar(FiltroAuditoria filtro, int pagina, int tamanho) {
        int paginaValida = Math.max(pagina, 0);
        int tamanhoValido = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO_PAGINA);
        PageRequest pageRequest = PageRequest.of(paginaValida, tamanhoValido, Sort.by(Sort.Direction.DESC, "dataHora"));

        Criteria criteria = montarCriterios(filtro == null ? new FiltroAuditoria() : filtro);
        long total = mongoTemplate.count(new Query(criteria), RegistroAuditoria.class);
        List<RegistroAuditoria> registros = mongoTemplate.find(new Query(criteria).with(pageRequest), RegistroAuditoria.class);

        return new PageImpl<>(protegerDadosPessoais(registros), pageRequest, total);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public RegistroAuditoria buscarPorId(String id) {
        RegistroAuditoria registro = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Registro de auditoria não encontrado."));
        List<RegistroAuditoria> lista = new ArrayList<>();
        lista.add(registro);
        return protegerDadosPessoais(lista).get(0);
    }

    private Criteria montarCriterios(FiltroAuditoria filtro) {
        List<Criteria> condicoes = new ArrayList<>();
        if (temTexto(filtro.getEntidade())) {
            condicoes.add(Criteria.where("entidade").is(filtro.getEntidade().trim()));
        }
        if (temTexto(filtro.getEntidadeId())) {
            condicoes.add(Criteria.where("entidadeId").is(filtro.getEntidadeId().trim()));
        }
        if (temTexto(filtro.getUsuario())) {
            condicoes.add(Criteria.where("usuario").is(filtro.getUsuario().trim()));
        }
        if (filtro.getOperacao() != null) {
            condicoes.add(Criteria.where("operacao").is(filtro.getOperacao().name()));
        }
        if (filtro.getDataInicio() != null) {
            condicoes.add(Criteria.where("dataHora").gte(filtro.getDataInicio().atStartOfDay()));
        }
        if (filtro.getDataFim() != null) {
            condicoes.add(Criteria.where("dataHora").lt(filtro.getDataFim().plusDays(1).atStartOfDay()));
        }
        if (condicoes.isEmpty()) {
            return new Criteria();
        }
        return new Criteria().andOperator(condicoes.toArray(new Criteria[0]));
    }

    // ===================== Proteção de dados pessoais =====================

    /**
     * Para o GERENTE (responsável pelo atendimento) os dados são exibidos integralmente.
     * Para o ADMIN, nome, telefone e endereço de clientes são mascarados na exibição.
     */
    List<RegistroAuditoria> protegerDadosPessoais(List<RegistroAuditoria> registros) {
        if (usuarioLogado.possuiPerfil(Perfil.GERENTE)) {
            return registros;
        }
        List<RegistroAuditoria> resultado = new ArrayList<>();
        for (RegistroAuditoria registro : registros) {
            if (!ENTIDADE_CLIENTE.equals(registro.getEntidade())) {
                resultado.add(registro);
                continue;
            }
            List<CampoAlterado> mascarados = new ArrayList<>();
            for (CampoAlterado campo : registro.getAlteracoes()) {
                if (CAMPOS_DADOS_PESSOAIS.contains(campo.getCampo())) {
                    mascarados.add(new CampoAlterado(campo.getCampo(),
                            mascarar(campo.getCampo(), campo.getValorAnterior()),
                            mascarar(campo.getCampo(), campo.getValorNovo())));
                } else {
                    mascarados.add(campo);
                }
            }
            resultado.add(registro.copiaParaExibicao(mascarados));
        }
        return resultado;
    }

    static String mascarar(String campo, String valor) {
        if (valor == null || valor.isEmpty()) {
            return valor;
        }
        if ("telefone".equals(campo)) {
            String digitos = valor.replaceAll("\\D", "");
            if (digitos.length() >= 4) {
                return "(**) *****-" + digitos.substring(digitos.length() - 4);
            }
            return "****";
        }
        return valor.charAt(0) + "***";
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
