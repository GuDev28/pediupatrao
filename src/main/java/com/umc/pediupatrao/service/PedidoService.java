package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Regras de negócio dos pedidos (RF02, RF03, RF04) com auditoria (RF05).
 *
 * Autorização por método (além das regras de rota do SecurityConfig):
 * - consulta: ADMIN, GERENTE e ATENDENTE;
 * - registro, alteração de itens e avanço de status: GERENTE e ATENDENTE;
 * - desconto e cancelamento: somente GERENTE.
 */
@Service
public class PedidoService {

    public static final int QUANTIDADE_MAXIMA_POR_ITEM = 99;
    public static final int MAXIMO_DE_ITENS = 50;
    public static final int TAMANHO_MAXIMO_OBSERVACAO = 500;
    public static final int TAMANHO_MINIMO_JUSTIFICATIVA = 10;
    public static final int TAMANHO_MAXIMO_JUSTIFICATIVA = 500;

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;
    private final UsuarioLogado usuarioLogado;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository, AuditoriaService auditoriaService,
            UsuarioLogado usuarioLogado, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
        this.usuarioLogado = usuarioLogado;
        this.clock = clock;
    }

    // ===================== Consulta =====================

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAllByOrderByDataHoraEntradaDesc();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPorStatus(StatusPedido status) {
        if (status == null) {
            return listarPedidos();
        }
        return pedidoRepository.findByStatusOrderByDataHoraEntradaDesc(status);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return buscar(id);
    }

    // ===================== RF02 - Registro (entrada) =====================

    /**
     * Registra a entrada do pedido: status RECEBIDO, data/hora de entrada e usuário
     * responsável. Preços vêm do cadastro de produtos e o total é calculado aqui.
     */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido criarPedido(PedidoRequest request) {
        if (request == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }
        LocalDateTime agora = LocalDateTime.now(clock);
        String usuario = usuarioLogado.getUsername();

        Pedido pedido = new Pedido();
        preencherDados(pedido, request);
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.recalcularValores();

        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(agora);
        pedido.setRegistradoPor(usuario);
        pedido.getHistorico().add(new HistoricoStatus(null, StatusPedido.RECEBIDO, agora, usuario,
                usuarioLogado.getNomePerfil()));

        Pedido salvo = pedidoRepository.save(pedido);

        Alteracoes alteracoes = new Alteracoes()
                .incluir("cliente", salvo.getClienteNome())
                .incluir("tipoEntrega", salvo.getTipoEntrega())
                .incluir("itens", salvo.getResumoItens())
                .incluir("observacao", salvo.getObservacao())
                .incluir("subtotal", salvo.getSubtotal())
                .incluir("valorTotal", salvo.getValorTotal())
                .incluir("status", salvo.getStatus())
                .incluir("dataHoraEntrada", salvo.getDataHoraEntrada())
                .incluir("registradoPor", salvo.getRegistradoPor());
        auditoriaService.registrar(TipoOperacao.INCLUSAO_PEDIDO, AuditoriaService.ENTIDADE_PEDIDO,
                salvo.getId(), "Pedido registrado (entrada)", alteracoes.getCampos(), null);
        return salvo;
    }

    /**
     * Altera itens, cliente, tipo de entrega ou observação. Só é permitido enquanto o
     * pedido está RECEBIDO (antes de ir para a cozinha). O desconto já autorizado é mantido.
     */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido atualizarPedido(String id, PedidoRequest request) {
        Pedido pedido = buscar(id);
        verificarNaoCancelado(pedido);
        if (pedido.getStatus() != StatusPedido.RECEBIDO) {
            throw new RegraNegocioException("Os itens só podem ser alterados enquanto o pedido está RECEBIDO "
                    + "(status atual: " + pedido.getStatus().getDescricao() + ").");
        }
        if (request == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }

        String clienteAnterior = pedido.getClienteNome();
        Object tipoEntregaAnterior = pedido.getTipoEntrega();
        String itensAnteriores = pedido.getResumoItens();
        String observacaoAnterior = pedido.getObservacao();
        BigDecimal subtotalAnterior = pedido.getSubtotal();
        BigDecimal descontoAnterior = pedido.getValorDesconto();
        BigDecimal totalAnterior = pedido.getValorTotal();

        preencherDados(pedido, request);
        pedido.recalcularValores();

        Alteracoes alteracoes = new Alteracoes()
                .comparar("cliente", clienteAnterior, pedido.getClienteNome())
                .comparar("tipoEntrega", tipoEntregaAnterior, pedido.getTipoEntrega())
                .comparar("itens", itensAnteriores, pedido.getResumoItens())
                .comparar("observacao", observacaoAnterior, pedido.getObservacao())
                .comparar("subtotal", subtotalAnterior, pedido.getSubtotal())
                .comparar("valorDesconto", descontoAnterior, pedido.getValorDesconto())
                .comparar("valorTotal", totalAnterior, pedido.getValorTotal());
        if (alteracoes.isVazia()) {
            throw new RegraNegocioException("Nenhuma alteração foi informada para o pedido.");
        }

        pedido.setDataHoraUltimaAlteracao(LocalDateTime.now(clock));
        pedido.setAlteradoPor(usuarioLogado.getUsername());
        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(TipoOperacao.ALTERACAO_PEDIDO, AuditoriaService.ENTIDADE_PEDIDO,
                salvo.getId(), "Pedido alterado", alteracoes.getCampos(), null);
        return salvo;
    }

    // ===================== RF03 - Transições de status =====================

    /** Mantém a assinatura original da API: status recebido como texto. */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido atualizarStatus(String id, String status) {
        StatusPedido novoStatus = StatusPedido.deTexto(status);
        if (novoStatus == null) {
            throw new RegraNegocioException("Status inválido: '" + status + "'. Valores aceitos: "
                    + "EM_PREPARACAO, PRONTO, SAIU_PARA_ENTREGA, RETIRADO, FINALIZADO.");
        }
        return alterarStatus(id, novoStatus);
    }

    /**
     * Aplica uma transição do fluxo obrigatório, registrando data/hora e responsável.
     * Na saída (SAIU_PARA_ENTREGA ou RETIRADO) grava obrigatoriamente data/hora e
     * responsável pela saída. Cancelamento não passa por aqui.
     */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido alterarStatus(String id, StatusPedido novoStatus) {
        Pedido pedido = buscar(id);
        verificarNaoCancelado(pedido);
        StatusPedido atual = pedido.getStatus();

        if (novoStatus == null) {
            throw new RegraNegocioException("Informe o novo status do pedido.");
        }
        if (novoStatus == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Para cancelar, use a operação de cancelamento "
                    + "(exclusiva do GERENTE e com justificativa obrigatória).");
        }
        if (atual.isFinal()) {
            throw new RegraNegocioException("O pedido já está " + atual.getDescricao()
                    + " e não admite mudança de status.");
        }
        if (!atual.podeAvancarPara(novoStatus)) {
            throw new RegraNegocioException("Transição inválida: " + atual + " -> " + novoStatus
                    + ". Próximo status permitido: " + pedido.getProximoStatus() + ".");
        }
        if (novoStatus.isSaida() && pedido.getTipoEntrega() == null) {
            throw new RegraNegocioException("Pedido sem tipo de entrega definido: altere o pedido antes da saída.");
        }
        if (novoStatus.isSaida() && novoStatus != pedido.getTipoEntrega().getStatusDeSaida()) {
            throw new RegraNegocioException("Pedido do tipo " + pedido.getTipoEntrega().getDescricao()
                    + " deve sair como " + pedido.getTipoEntrega().getStatusDeSaida() + ".");
        }

        LocalDateTime agora = LocalDateTime.now(clock);
        String usuario = usuarioLogado.getUsername();
        Alteracoes alteracoes = new Alteracoes().comparar("status", atual, novoStatus);

        pedido.setStatus(novoStatus);
        if (novoStatus.isSaida()) {
            pedido.setDataHoraSaida(agora);
            pedido.setResponsavelSaida(usuario);
            alteracoes.incluir("dataHoraSaida", agora).incluir("responsavelSaida", usuario);
        }
        if (novoStatus == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(agora);
            pedido.setFinalizadoPor(usuario);
            alteracoes.incluir("dataHoraFinalizacao", agora).incluir("finalizadoPor", usuario);
        }
        pedido.getHistorico().add(new HistoricoStatus(atual, novoStatus, agora, usuario,
                usuarioLogado.getNomePerfil()));

        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(TipoOperacao.ALTERACAO_STATUS_PEDIDO, AuditoriaService.ENTIDADE_PEDIDO,
                salvo.getId(), "Status: " + atual.getDescricao() + " -> " + novoStatus.getDescricao(),
                alteracoes.getCampos(), null);
        return salvo;
    }

    // ===================== RF04 - Desconto e cancelamento =====================

    /**
     * Aplica (ou substitui) o desconto percentual do pedido. Exclusivo do GERENTE,
     * limitado a 20% e permitido apenas antes da saída para entrega ou retirada.
     * Informar 0% remove o desconto.
     */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String justificativa) {
        Pedido pedido = buscar(id);
        verificarNaoCancelado(pedido);
        if (!pedido.getStatus().isAntesDaSaida()) {
            throw new RegraNegocioException("Desconto só pode ser aplicado antes da saída para entrega ou retirada "
                    + "(status atual: " + pedido.getStatus().getDescricao() + ").");
        }
        if (percentual == null) {
            throw new RegraNegocioException("Informe o percentual de desconto.");
        }
        if (percentual.compareTo(BigDecimal.ZERO) < 0
                || percentual.compareTo(Pedido.DESCONTO_MAXIMO_PERCENTUAL) > 0) {
            throw new RegraNegocioException("O desconto deve estar entre 0% e "
                    + Pedido.DESCONTO_MAXIMO_PERCENTUAL.toPlainString() + "% do valor do pedido.");
        }
        if (percentual.stripTrailingZeros().scale() > 2) {
            throw new RegraNegocioException("Use no máximo duas casas decimais no percentual.");
        }
        BigDecimal novoPercentual = percentual.setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentualAnterior = pedido.getPercentualDesconto() == null
                ? BigDecimal.ZERO : pedido.getPercentualDesconto();
        if (novoPercentual.compareTo(percentualAnterior) == 0) {
            throw new RegraNegocioException("O pedido já possui desconto de " + novoPercentual.toPlainString() + "%.");
        }
        String justificativaLimpa = limparTexto(justificativa);
        if (justificativaLimpa != null && justificativaLimpa.length() > TAMANHO_MAXIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("A justificativa deve ter no máximo "
                    + TAMANHO_MAXIMO_JUSTIFICATIVA + " caracteres.");
        }

        BigDecimal descontoAnterior = pedido.getValorDesconto();
        BigDecimal totalAnterior = pedido.getValorTotal();

        pedido.setPercentualDesconto(novoPercentual);
        pedido.recalcularValores();
        pedido.setDescontoAplicadoPor(usuarioLogado.getUsername());
        pedido.setDataHoraDesconto(LocalDateTime.now(clock));
        pedido.setJustificativaDesconto(justificativaLimpa);

        Pedido salvo = pedidoRepository.save(pedido);

        Alteracoes alteracoes = new Alteracoes()
                .comparar("percentualDesconto", percentualAnterior, novoPercentual)
                .comparar("valorDesconto", descontoAnterior, salvo.getValorDesconto())
                .comparar("valorTotal", totalAnterior, salvo.getValorTotal());
        auditoriaService.registrar(TipoOperacao.APLICACAO_DESCONTO, AuditoriaService.ENTIDADE_PEDIDO,
                salvo.getId(), "Desconto de " + novoPercentual.toPlainString() + "% aplicado",
                alteracoes.getCampos(), justificativaLimpa);
        return salvo;
    }

    /**
     * Cancela o pedido. Exclusivo do GERENTE, exige justificativa e só é permitido
     * antes da saída. Itens, valores e histórico são preservados; depois disso o
     * pedido não aceita nenhuma alteração.
     */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        Pedido pedido = buscar(id);
        verificarNaoCancelado(pedido);
        if (!pedido.getStatus().isAntesDaSaida()) {
            throw new RegraNegocioException("Pedido com status " + pedido.getStatus().getDescricao()
                    + " não pode ser cancelado: o cancelamento só é permitido antes da saída para entrega ou retirada.");
        }
        String justificativaLimpa = limparTexto(justificativa);
        if (justificativaLimpa == null || justificativaLimpa.length() < TAMANHO_MINIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("A justificativa do cancelamento é obrigatória (mínimo de "
                    + TAMANHO_MINIMO_JUSTIFICATIVA + " caracteres).");
        }
        if (justificativaLimpa.length() > TAMANHO_MAXIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("A justificativa deve ter no máximo "
                    + TAMANHO_MAXIMO_JUSTIFICATIVA + " caracteres.");
        }

        LocalDateTime agora = LocalDateTime.now(clock);
        String usuario = usuarioLogado.getUsername();
        StatusPedido anterior = pedido.getStatus();

        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setDataHoraCancelamento(agora);
        pedido.setCanceladoPor(usuario);
        pedido.setJustificativaCancelamento(justificativaLimpa);
        pedido.getHistorico().add(new HistoricoStatus(anterior, StatusPedido.CANCELADO, agora, usuario,
                usuarioLogado.getNomePerfil()));

        Pedido salvo = pedidoRepository.save(pedido);

        Alteracoes alteracoes = new Alteracoes()
                .comparar("status", anterior, StatusPedido.CANCELADO)
                .incluir("dataHoraCancelamento", agora)
                .incluir("canceladoPor", usuario);
        auditoriaService.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, AuditoriaService.ENTIDADE_PEDIDO,
                salvo.getId(), "Pedido cancelado (status anterior: " + anterior.getDescricao() + ")",
                alteracoes.getCampos(), justificativaLimpa);
        return salvo;
    }

    // ===================== apoio =====================

    private Pedido buscar(String id) {
        if (id == null || id.isBlank()) {
            throw new RecursoNaoEncontradoException("Pedido não encontrado.");
        }
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    private void verificarNaoCancelado(Pedido pedido) {
        if (pedido.isCancelado()) {
            throw new RegraNegocioException("Pedido cancelado não pode ser alterado.");
        }
    }

    /**
     * Valida e copia os dados permitidos da requisição para o pedido. Preço e nome de
     * cada item são lidos do cadastro de produtos, nunca da requisição.
     */
    private void preencherDados(Pedido pedido, PedidoRequest request) {
        if (request.getClienteId() == null || request.getClienteId().isBlank()) {
            throw new RegraNegocioException("Selecione o cliente do pedido.");
        }
        Cliente cliente = clienteRepository.findById(request.getClienteId())
                .orElseThrow(() -> new RegraNegocioException("Cliente informado não existe."));

        if (request.getTipoEntrega() == null) {
            throw new RegraNegocioException("Informe se o pedido é para ENTREGA ou RETIRADA.");
        }

        String observacao = limparTexto(request.getObservacao());
        if (observacao != null && observacao.length() > TAMANHO_MAXIMO_OBSERVACAO) {
            throw new RegraNegocioException("A observação deve ter no máximo " + TAMANHO_MAXIMO_OBSERVACAO + " caracteres.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        if (request.getItens() != null) {
            for (ItemPedidoRequest itemRequest : request.getItens()) {
                // Linhas vazias do formulário são ignoradas
                if (itemRequest == null || itemRequest.getProdutoId() == null || itemRequest.getProdutoId().isBlank()) {
                    continue;
                }
                itens.add(criarItem(itemRequest));
            }
        }
        if (itens.isEmpty()) {
            throw new RegraNegocioException("O pedido deve ter pelo menos um item.");
        }
        if (itens.size() > MAXIMO_DE_ITENS) {
            throw new RegraNegocioException("O pedido pode ter no máximo " + MAXIMO_DE_ITENS + " itens.");
        }

        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(request.getTipoEntrega());
        pedido.setObservacao(observacao);
        pedido.setItens(itens);
    }

    private ItemPedido criarItem(ItemPedidoRequest itemRequest) {
        Integer quantidade = itemRequest.getQuantidade();
        if (quantidade == null || quantidade < 1 || quantidade > QUANTIDADE_MAXIMA_POR_ITEM) {
            throw new RegraNegocioException("Quantidade inválida: informe de 1 a " + QUANTIDADE_MAXIMA_POR_ITEM + " por item.");
        }
        Produto produto = produtoRepository.findById(itemRequest.getProdutoId())
                .orElseThrow(() -> new RegraNegocioException("Produto informado não existe."));
        if (Boolean.FALSE.equals(produto.getAtivo())) {
            throw new RegraNegocioException("O produto '" + produto.getNome() + "' está inativo.");
        }
        if (produto.getPreco() == null || produto.getPreco() <= 0) {
            throw new RegraNegocioException("O produto '" + produto.getNome() + "' está sem preço válido.");
        }
        ItemPedido item = new ItemPedido(produto.getId(), produto.getNome(), quantidade,
                BigDecimal.valueOf(produto.getPreco()));
        item.setCategoria(produto.getTipo());
        return item;
    }

    private static String limparTexto(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
