package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private RegistroAuditoriaRepository repository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private UsuarioLogado usuarioLogado;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));
    private AuditoriaService service;

    @BeforeEach
    void configurar() {
        service = new AuditoriaService(repository, mongoTemplate, usuarioLogado, clock);
    }

    @Test
    void registroIdentificaUsuarioPerfilDataHoraEntidadeECampos() {
        when(usuarioLogado.getUsername()).thenReturn("atendente01");
        when(usuarioLogado.getNomePerfil()).thenReturn("ATENDENTE");
        when(repository.save(any(RegistroAuditoria.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        List<CampoAlterado> campos = new ArrayList<>();
        campos.add(new CampoAlterado("logradouro", "Rua das Flores", "Av. Brasil"));

        RegistroAuditoria registro = service.registrar(TipoOperacao.ALTERACAO_CLIENTE, "Cliente", "c1",
                "Dados cadastrais alterados", campos, null);

        assertThat(registro.getUsuario()).isEqualTo("atendente01");
        assertThat(registro.getPerfil()).isEqualTo("ATENDENTE");
        assertThat(registro.getDataHora()).isEqualTo(LocalDateTime.of(2026, 9, 23, 14, 35));
        assertThat(registro.getOperacao()).isEqualTo(TipoOperacao.ALTERACAO_CLIENTE);
        assertThat(registro.getEntidadeId()).isEqualTo("c1");
        assertThat(registro.getAlteracoes()).hasSize(1);
        assertThat(registro.getAlteracoes().get(0).getValorNovo()).isEqualTo("Av. Brasil");
    }

    @Test
    void adminVeDadosPessoaisDeClientesMascarados() {
        when(usuarioLogado.possuiPerfil(Perfil.GERENTE)).thenReturn(false);

        List<RegistroAuditoria> exibidos = service.protegerDadosPessoais(registrosDeExemplo());

        CampoAlterado telefone = exibidos.get(0).getAlteracoes().get(0);
        CampoAlterado logradouro = exibidos.get(0).getAlteracoes().get(1);
        CampoAlterado cidade = exibidos.get(0).getAlteracoes().get(2);
        assertThat(telefone.getValorAnterior()).isEqualTo("(**) *****-7777");
        assertThat(telefone.getValorNovo()).isEqualTo("(**) *****-6666");
        assertThat(logradouro.getValorAnterior()).isEqualTo("R***");
        assertThat(cidade.getValorNovo()).isEqualTo("Suzano"); // cidade não identifica a pessoa
        // registros de pedidos não são alterados
        assertThat(exibidos.get(1).getAlteracoes().get(0).getValorNovo()).isEqualTo("EM_PREPARACAO");
    }

    @Test
    void gerenteVeDadosCompletos() {
        when(usuarioLogado.possuiPerfil(Perfil.GERENTE)).thenReturn(true);

        List<RegistroAuditoria> exibidos = service.protegerDadosPessoais(registrosDeExemplo());

        assertThat(exibidos.get(0).getAlteracoes().get(1).getValorAnterior()).isEqualTo("Rua das Flores");
    }

    private List<RegistroAuditoria> registrosDeExemplo() {
        List<CampoAlterado> camposCliente = new ArrayList<>();
        camposCliente.add(new CampoAlterado("telefone", "(11) 98888-7777", "(11) 97777-6666"));
        camposCliente.add(new CampoAlterado("logradouro", "Rua das Flores", "Av. Brasil"));
        camposCliente.add(new CampoAlterado("cidade", "Mogi das Cruzes", "Suzano"));
        List<CampoAlterado> camposPedido = new ArrayList<>();
        camposPedido.add(new CampoAlterado("status", "RECEBIDO", "EM_PREPARACAO"));

        LocalDateTime agora = LocalDateTime.now(clock);
        List<RegistroAuditoria> registros = new ArrayList<>();
        registros.add(new RegistroAuditoria(agora, "atendente01", "ATENDENTE", TipoOperacao.ALTERACAO_CLIENTE,
                "Cliente", "c1", "Dados alterados", camposCliente, null));
        registros.add(new RegistroAuditoria(agora, "gerente01", "GERENTE", TipoOperacao.ALTERACAO_STATUS_PEDIDO,
                "Pedido", "p1", "Status", camposPedido, null));
        return registros;
    }
}
