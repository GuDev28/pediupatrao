package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    private static final String ID_PEDIDO = "6650a1b2c3d4e5f6a7b8c9d0";

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private UsuarioLogado usuarioLogado;

    // 23/09/2026 14:35 no horário de Brasília
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final LocalDateTime agora = LocalDateTime.of(2026, 9, 23, 14, 35);

    private PedidoService service;

    @BeforeEach
    void configurar() {
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository,
                auditoriaService, usuarioLogado, clock);
        logarComo("atendente01", "ATENDENTE");
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocacao -> {
            Pedido pedido = invocacao.getArgument(0);
            if (pedido.getId() == null) {
                pedido.setId(ID_PEDIDO);
            }
            return pedido;
        });
    }

    // ===================== RF02 =====================

    @Test
    void registraEntradaComTotalCalculadoPeloServidor() {
        cadastrarClienteEProdutos();
        PedidoRequest request = requisicao(item("calabresa", 2), item("refri", 1));

        Pedido pedido = service.criarPedido(request);

        assertThat(pedido.getItens()).hasSize(2);
        assertThat(pedido.getItens().get(0).getPrecoUnitario()).isEqualByComparingTo("54.90");
        assertThat(pedido.getSubtotal()).isEqualByComparingTo("123.80");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("123.80");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.RECEBIDO);
        assertThat(pedido.getDataHoraEntrada()).isEqualTo(agora);
        assertThat(pedido.getRegistradoPor()).isEqualTo("atendente01");
        assertThat(pedido.getClienteNome()).isEqualTo("Maria Souza");
        assertThat(pedido.getHistorico()).hasSize(1);
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_PEDIDO), eq("Pedido"), eq(ID_PEDIDO),
                anyString(), anyList(), isNull());
    }

    @Test
    void ignoraTotalEStatusEnviadosNaRequisicaoHttp() throws Exception {
        cadastrarClienteEProdutos();
        // Mesmo comportamento do ObjectMapper do Spring Boot: campos desconhecidos são ignorados
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String json = "{\"clienteId\":\"cli1\",\"tipoEntrega\":\"ENTREGA\","
                + "\"itens\":[{\"produtoId\":\"calabresa\",\"quantidade\":1,\"precoUnitario\":0.01}],"
                + "\"valorTotal\":0.01,\"percentualDesconto\":90,\"status\":\"FINALIZADO\"}";

        Pedido pedido = service.criarPedido(mapper.readValue(json, PedidoRequest.class));

        assertThat(pedido.getValorTotal()).isEqualByComparingTo("54.90");
        assertThat(pedido.getPercentualDesconto()).isEqualByComparingTo("0");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.RECEBIDO);
    }

    @Test
    void rejeitaPedidoSemItens() {
        when(clienteRepository.findById("cli1")).thenReturn(Optional.of(cliente()));
        PedidoRequest request = requisicao();

        assertThatThrownBy(() -> service.criarPedido(request))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("pelo menos um item");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void rejeitaQuantidadeInvalidaEProdutoInativo() {
        cadastrarClienteEProdutos();
        Produto inativo = produto("velha", "Pizza Antiga", 30.0);
        inativo.setAtivo(false);
        when(produtoRepository.findById("velha")).thenReturn(Optional.of(inativo));

        assertThatThrownBy(() -> service.criarPedido(requisicao(item("calabresa", 0))))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("Quantidade inválida");
        assertThatThrownBy(() -> service.criarPedido(requisicao(item("calabresa", 100))))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("Quantidade inválida");
        assertThatThrownBy(() -> service.criarPedido(requisicao(item("velha", 1))))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("inativo");
    }

    // ===================== RF03 =====================

    @Test
    void percorreOFluxoCompletoRegistrandoSaidaEResponsavel() {
        Pedido pedido = pedidoExistente(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);

        service.alterarStatus(ID_PEDIDO, StatusPedido.EM_PREPARACAO);
        service.alterarStatus(ID_PEDIDO, StatusPedido.PRONTO);
        assertThat(pedido.getDataHoraSaida()).isNull();

        logarComo("gerente01", "GERENTE");
        service.alterarStatus(ID_PEDIDO, StatusPedido.SAIU_PARA_ENTREGA);
        assertThat(pedido.getDataHoraSaida()).isEqualTo(agora);
        assertThat(pedido.getResponsavelSaida()).isEqualTo("gerente01");

        service.alterarStatus(ID_PEDIDO, StatusPedido.FINALIZADO);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.FINALIZADO);
        assertThat(pedido.getFinalizadoPor()).isEqualTo("gerente01");
        assertThat(pedido.getHistorico()).hasSize(4);
    }

    @Test
    void auditaMudancaDeStatusComValorAnteriorENovo() {
        pedidoExistente(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);

        service.alterarStatus(ID_PEDIDO, StatusPedido.EM_PREPARACAO);

        List<CampoAlterado> campos = capturarCamposAuditados(TipoOperacao.ALTERACAO_STATUS_PEDIDO);
        assertThat(campos).hasSize(1);
        assertThat(campos.get(0).getCampo()).isEqualTo("status");
        assertThat(campos.get(0).getValorAnterior()).isEqualTo("RECEBIDO");
        assertThat(campos.get(0).getValorNovo()).isEqualTo("EM_PREPARACAO");
    }

    @Test
    void impedeFinalizarPedidoEmPreparacao() {
        pedidoExistente(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA);

        assertThatThrownBy(() -> service.alterarStatus(ID_PEDIDO, StatusPedido.FINALIZADO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Transição inválida");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void pedidoDeRetiradaNaoPodeSairParaEntrega() {
        pedidoExistente(StatusPedido.PRONTO, TipoEntrega.RETIRADA);

        assertThatThrownBy(() -> service.alterarStatus(ID_PEDIDO, StatusPedido.SAIU_PARA_ENTREGA))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("RETIRADO");
    }

    @Test
    void cancelamentoNaoPassaPelaTrocaDeStatusComum() {
        pedidoExistente(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);

        assertThatThrownBy(() -> service.atualizarStatus(ID_PEDIDO, "CANCELADO"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("operação de cancelamento");
    }

    @Test
    void statusDesconhecidoEhRejeitado() {
        assertThatThrownBy(() -> service.atualizarStatus(ID_PEDIDO, "ENTREGUE"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Status inválido");
    }

    @Test
    void pedidoCanceladoNaoAceitaNenhumaAlteracao() {
        pedidoExistente(StatusPedido.CANCELADO, TipoEntrega.ENTREGA);
        logarComo("gerente01", "GERENTE");

        assertThatThrownBy(() -> service.alterarStatus(ID_PEDIDO, StatusPedido.EM_PREPARACAO))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("cancelado");
        assertThatThrownBy(() -> service.aplicarDesconto(ID_PEDIDO, new BigDecimal("10"), null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("cancelado");
        assertThatThrownBy(() -> service.cancelar(ID_PEDIDO, "Tentativa de cancelar de novo"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("cancelado");
        assertThatThrownBy(() -> service.atualizarPedido(ID_PEDIDO, requisicao(item("calabresa", 1))))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("cancelado");
        verify(pedidoRepository, never()).save(any());
    }

    // ===================== RF04 - cancelamento =====================

    @Test
    void cancelamentoExigeJustificativa() {
        pedidoExistente(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA);
        logarComo("gerente01", "GERENTE");

        assertThatThrownBy(() -> service.cancelar(ID_PEDIDO, null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("justificativa");
        assertThatThrownBy(() -> service.cancelar(ID_PEDIDO, "   curta "))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("justificativa");
    }

    @Test
    void cancelamentoPreservaDadosERegistraAuditoriaComJustificativa() {
        Pedido pedido = pedidoExistente(StatusPedido.PRONTO, TipoEntrega.ENTREGA);
        BigDecimal totalAntes = pedido.getValorTotal();
        logarComo("gerente01", "GERENTE");

        service.cancelar(ID_PEDIDO, "Cliente desistiu por telefone");

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(pedido.getCanceladoPor()).isEqualTo("gerente01");
        assertThat(pedido.getDataHoraCancelamento()).isEqualTo(agora);
        assertThat(pedido.getJustificativaCancelamento()).isEqualTo("Cliente desistiu por telefone");
        assertThat(pedido.getItens()).hasSize(1);
        assertThat(pedido.getValorTotal()).isEqualByComparingTo(totalAntes);
        verify(auditoriaService).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), eq("Pedido"), eq(ID_PEDIDO),
                anyString(), anyList(), eq("Cliente desistiu por telefone"));
    }

    @Test
    void naoCancelaPedidoQueJaSaiu() {
        pedidoExistente(StatusPedido.SAIU_PARA_ENTREGA, TipoEntrega.ENTREGA);
        logarComo("gerente01", "GERENTE");

        assertThatThrownBy(() -> service.cancelar(ID_PEDIDO, "Cliente desistiu por telefone"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("antes da saída");
    }

    // ===================== RF04 - desconto =====================

    @Test
    void descontoDe20PorCentoRecalculaTotalEAudita() {
        Pedido pedido = pedidoExistente(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA); // subtotal 109,80
        logarComo("gerente01", "GERENTE");

        service.aplicarDesconto(ID_PEDIDO, new BigDecimal("20"), "Cliente fidelidade");

        assertThat(pedido.getValorDesconto()).isEqualByComparingTo("21.96");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("87.84");
        assertThat(pedido.getDescontoAplicadoPor()).isEqualTo("gerente01");

        List<CampoAlterado> campos = capturarCamposAuditados(TipoOperacao.APLICACAO_DESCONTO);
        assertThat(campos).extracting(CampoAlterado::getCampo)
                .containsExactly("percentualDesconto", "valorDesconto", "valorTotal");
        assertThat(campos.get(2).getValorAnterior()).isEqualTo("109.80");
        assertThat(campos.get(2).getValorNovo()).isEqualTo("87.84");
    }

    @Test
    void descontoAcimaDe20PorCentoEhBloqueado() {
        pedidoExistente(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);
        logarComo("gerente01", "GERENTE");

        assertThatThrownBy(() -> service.aplicarDesconto(ID_PEDIDO, new BigDecimal("20.01"), null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("20");
        assertThatThrownBy(() -> service.aplicarDesconto(ID_PEDIDO, new BigDecimal("-5"), null))
                .isInstanceOf(RegraNegocioException.class);
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void descontoSoAntesDaSaida() {
        pedidoExistente(StatusPedido.RETIRADO, TipoEntrega.RETIRADA);
        logarComo("gerente01", "GERENTE");

        assertThatThrownBy(() -> service.aplicarDesconto(ID_PEDIDO, new BigDecimal("5"), null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("antes da saída");
    }

    // ===================== alteração de itens =====================

    @Test
    void itensSoPodemSerAlteradosEnquantoRecebido() {
        pedidoExistente(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA);

        assertThatThrownBy(() -> service.atualizarPedido(ID_PEDIDO, requisicao(item("calabresa", 3))))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("RECEBIDO");
    }

    @Test
    void alteracaoDeItensMantemDescontoEAuditaDiferencas() {
        Pedido pedido = pedidoExistente(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);
        pedido.setPercentualDesconto(new BigDecimal("10"));
        pedido.recalcularValores();
        cadastrarClienteEProdutos();

        service.atualizarPedido(ID_PEDIDO, requisicao(item("calabresa", 3)));

        assertThat(pedido.getSubtotal()).isEqualByComparingTo("164.70");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("148.23");
        List<CampoAlterado> campos = capturarCamposAuditados(TipoOperacao.ALTERACAO_PEDIDO);
        assertThat(campos).extracting(CampoAlterado::getCampo).contains("itens", "subtotal", "valorTotal");
    }

    // ===================== apoio =====================

    private void logarComo(String usuario, String perfil) {
        lenient().when(usuarioLogado.getUsername()).thenReturn(usuario);
        lenient().when(usuarioLogado.getNomePerfil()).thenReturn(perfil);
    }

    private Cliente cliente() {
        Cliente cliente = new Cliente();
        cliente.setId("cli1");
        cliente.setNome("Maria Souza");
        cliente.setTelefone("(11) 98888-7777");
        return cliente;
    }

    private Produto produto(String id, String nome, double preco) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }

    private void cadastrarClienteEProdutos() {
        lenient().when(clienteRepository.findById("cli1")).thenReturn(Optional.of(cliente()));
        lenient().when(produtoRepository.findById("calabresa"))
                .thenReturn(Optional.of(produto("calabresa", "Pizza Calabresa", 54.90)));
        lenient().when(produtoRepository.findById("refri"))
                .thenReturn(Optional.of(produto("refri", "Refrigerante 2L", 14.00)));
    }

    private ItemPedidoRequest item(String produtoId, int quantidade) {
        return new ItemPedidoRequest(produtoId, quantidade);
    }

    private PedidoRequest requisicao(ItemPedidoRequest... itens) {
        PedidoRequest request = new PedidoRequest();
        request.setClienteId("cli1");
        request.setTipoEntrega(TipoEntrega.ENTREGA);
        List<ItemPedidoRequest> lista = new ArrayList<>();
        for (ItemPedidoRequest item : itens) {
            lista.add(item);
        }
        request.setItens(lista);
        return request;
    }

    /** Pedido já gravado: 2x Pizza Calabresa (subtotal 109,80). */
    private Pedido pedidoExistente(StatusPedido status, TipoEntrega tipoEntrega) {
        Pedido pedido = new Pedido();
        pedido.setId(ID_PEDIDO);
        pedido.setClienteId("cli1");
        pedido.setClienteNome("Maria Souza");
        pedido.setTipoEntrega(tipoEntrega);
        List<ItemPedido> itens = new ArrayList<>();
        itens.add(new ItemPedido("calabresa", "Pizza Calabresa", 2, new BigDecimal("54.90")));
        pedido.setItens(itens);
        pedido.recalcularValores();
        pedido.setStatus(status);
        pedido.setDataHoraEntrada(agora.minusMinutes(30));
        pedido.setRegistradoPor("atendente01");
        lenient().when(pedidoRepository.findById(ID_PEDIDO)).thenReturn(Optional.of(pedido));
        return pedido;
    }

    @SuppressWarnings("unchecked")
    private List<CampoAlterado> capturarCamposAuditados(TipoOperacao operacao) {
        ArgumentCaptor<List<CampoAlterado>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(operacao), eq("Pedido"), eq(ID_PEDIDO), anyString(),
                captor.capture(), any());
        return captor.getValue();
    }
}
