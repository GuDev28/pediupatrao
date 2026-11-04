package com.umc.pediupatrao.service.dashboard;

import static com.umc.pediupatrao.service.dashboard.PedidosDeTeste.calabresa;
import static com.umc.pediupatrao.service.dashboard.PedidosDeTeste.esfiha;
import static com.umc.pediupatrao.service.dashboard.PedidosDeTeste.item;
import static com.umc.pediupatrao.service.dashboard.PedidosDeTeste.margherita;
import static com.umc.pediupatrao.service.dashboard.PedidosDeTeste.pedido;
import static com.umc.pediupatrao.service.dashboard.PedidosDeTeste.pedidoComDesconto;
import static com.umc.pediupatrao.service.dashboard.PedidosDeTeste.refrigerante;
import static org.assertj.core.api.Assertions.assertThat;

import com.umc.pediupatrao.dto.dashboard.AgrupamentoSerie;
import com.umc.pediupatrao.dto.dashboard.DimensaoDistribuicao;
import com.umc.pediupatrao.dto.dashboard.ItemDistribuicao;
import com.umc.pediupatrao.dto.dashboard.OrdenacaoRanking;
import com.umc.pediupatrao.dto.dashboard.PedidoResumo;
import com.umc.pediupatrao.dto.dashboard.PontoSerie;
import com.umc.pediupatrao.dto.dashboard.ProdutoRanking;
import com.umc.pediupatrao.dto.dashboard.QuantidadePorStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regras de cálculo do dashboard (RF01 a RF04 e RF07), testadas com pedidos em memória.
 *
 * Cenário base (segunda-feira, 05/10/2026):
 * - A: FINALIZADO, entrega, 2 Calabresa + 1 Refrigerante = 123,80 (19h)
 * - B: EM_PREPARACAO, retirada, 1 Margherita = 52,90 (19h)
 * - C: CANCELADO, entrega, 1 Calabresa = 54,90 (12h)
 * - D: RECEBIDO, entrega, 1 Calabresa + 1 Refrigerante com 10% de desconto = 62,01 (20h)
 */
class CalculadoraDashboardTest {

    private static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 5);

    private Pedido a;
    private Pedido b;
    private Pedido c;
    private Pedido d;
    private List<Pedido> cenario;

    @BeforeEach
    void montarCenario() {
        a = pedido(SEGUNDA.atTime(19, 10), StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, calabresa(2), refrigerante(1));
        b = pedido(SEGUNDA.atTime(19, 40), StatusPedido.EM_PREPARACAO, TipoEntrega.RETIRADA, margherita(1));
        c = pedido(SEGUNDA.atTime(12, 5), StatusPedido.CANCELADO, TipoEntrega.ENTREGA, calabresa(1));
        d = pedidoComDesconto(SEGUNDA.atTime(20, 0), StatusPedido.RECEBIDO, TipoEntrega.ENTREGA, "10",
                calabresa(1), refrigerante(1));
        cenario = lista(a, b, c, d);
    }

    // ===================== RF01 - indicadores gerais =====================

    @Test
    void totalizaSomenteVendasValidasESeparaCancelamentosEDescontos() {
        CalculadoraDashboard.Totais totais = CalculadoraDashboard.totalizar(cenario);

        assertThat(totais.registrados()).isEqualTo(4);
        assertThat(totais.pedidosValidos()).isEqualTo(3);
        assertThat(totais.faturamento()).isEqualByComparingTo("238.71");
        assertThat(totais.ticketMedio()).isEqualByComparingTo("79.57");
        assertThat(totais.itensVendidos()).isEqualTo(6);
        assertThat(totais.cancelados()).isEqualTo(1);
        assertThat(totais.valorCancelado()).isEqualByComparingTo("54.90");
        assertThat(totais.taxaCancelamento()).isEqualByComparingTo("25.0");
        assertThat(totais.comDesconto()).isEqualTo(1);
        assertThat(totais.valorDescontos()).isEqualByComparingTo("6.89");
        assertThat(totais.emAndamento()).isEqualTo(2);
    }

    @Test
    void periodoSemPedidosNaoGeraDivisaoPorZero() {
        CalculadoraDashboard.Totais totais = CalculadoraDashboard.totalizar(new ArrayList<>());

        assertThat(totais.faturamento()).isEqualByComparingTo("0");
        assertThat(totais.ticketMedio()).isEqualByComparingTo("0");
        assertThat(totais.taxaCancelamento()).isEqualByComparingTo("0");
        assertThat(CalculadoraDashboard.totalizar(null).registrados()).isZero();
    }

    @Test
    void variacaoPercentualEmRelacaoAoPeriodoAnterior() {
        assertThat(CalculadoraDashboard.variacaoPercentual(new BigDecimal("1100"), new BigDecimal("1000")))
                .isEqualByComparingTo("10.0");
        assertThat(CalculadoraDashboard.variacaoPercentual(new BigDecimal("900"), new BigDecimal("1000")))
                .isEqualByComparingTo("-10.0");
        // sem base de comparação: null (a tela mostra "sem comparação")
        assertThat(CalculadoraDashboard.variacaoPercentual(new BigDecimal("500"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void pedidoAntigoSemStatusContaComoVendaENaoQuebraOCalculo() {
        Pedido legado = pedido(SEGUNDA.atTime(10, 0), null, null, calabresa(1));

        CalculadoraDashboard.Totais totais = CalculadoraDashboard.totalizar(lista(legado));

        assertThat(totais.pedidosValidos()).isEqualTo(1);
        assertThat(totais.emAndamento()).isZero();
        assertThat(totais.faturamento()).isEqualByComparingTo("54.90");
    }

    // ===================== RF02 - série temporal =====================

    @Test
    void serieDiariaPreencheDiasSemVendasComZero() {
        Pedido terca = pedido(SEGUNDA.plusDays(2).atTime(20, 0), StatusPedido.FINALIZADO, TipoEntrega.ENTREGA,
                margherita(2));
        List<Pedido> pedidos = lista(a, c, terca);

        List<PontoSerie> pontos = CalculadoraDashboard.serie(pedidos, SEGUNDA, SEGUNDA.plusDays(2),
                AgrupamentoSerie.DIA);

        assertThat(pontos).hasSize(3);
        assertThat(pontos.get(0).rotulo()).isEqualTo("05/10");
        assertThat(pontos.get(0).faturamento()).isEqualByComparingTo("123.80"); // C cancelado não entra
        assertThat(pontos.get(0).pedidos()).isEqualTo(1);
        assertThat(pontos.get(1).faturamento()).isEqualByComparingTo("0");
        assertThat(pontos.get(1).ticketMedio()).isEqualByComparingTo("0");
        assertThat(pontos.get(2).faturamento()).isEqualByComparingTo("105.80");
    }

    @Test
    void serieSemanalRecortaAPrimeiraEAUltimaSemanaNoPeriodo() {
        LocalDate quarta = LocalDate.of(2026, 10, 7);
        LocalDate terca = LocalDate.of(2026, 10, 20);
        Pedido semana2 = pedido(LocalDate.of(2026, 10, 14).atTime(19, 0), StatusPedido.FINALIZADO,
                TipoEntrega.ENTREGA, calabresa(1));

        List<PontoSerie> pontos = CalculadoraDashboard.serie(lista(semana2), quarta, terca, AgrupamentoSerie.SEMANA);

        assertThat(pontos).hasSize(3);
        assertThat(pontos.get(0).inicio()).isEqualTo(quarta);
        assertThat(pontos.get(0).fim()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(pontos.get(0).rotulo()).isEqualTo("Sem. 07/10");
        assertThat(pontos.get(1).inicio()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(pontos.get(1).pedidos()).isEqualTo(1);
        assertThat(pontos.get(2).fim()).isEqualTo(terca);
    }

    @Test
    void serieMensalUsaRotuloComMesEAno() {
        List<PontoSerie> pontos = CalculadoraDashboard.serie(cenario, LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 11, 3), AgrupamentoSerie.MES);

        assertThat(pontos).extracting(PontoSerie::rotulo).containsExactly("set/26", "out/26", "nov/26");
        assertThat(pontos.get(1).faturamento()).isEqualByComparingTo("238.71");
    }

    @Test
    void serieIgnoraPedidosForaDoPeriodoOuSemDataDeEntrada() {
        Pedido semData = pedido(null, StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, calabresa(1));
        Pedido depois = pedido(SEGUNDA.plusDays(1).atTime(10, 0), StatusPedido.FINALIZADO, TipoEntrega.ENTREGA,
                calabresa(1));

        List<PontoSerie> pontos = CalculadoraDashboard.serie(lista(semData, depois), SEGUNDA, SEGUNDA,
                AgrupamentoSerie.DIA);

        assertThat(pontos).hasSize(1);
        assertThat(pontos.get(0).pedidos()).isZero();
    }

    @Test
    void agrupamentoAutomaticoDependeDoTamanhoDoPeriodo() {
        assertThat(AgrupamentoSerie.automatico(30)).isEqualTo(AgrupamentoSerie.DIA);
        assertThat(AgrupamentoSerie.automatico(90)).isEqualTo(AgrupamentoSerie.SEMANA);
        assertThat(AgrupamentoSerie.automatico(366)).isEqualTo(AgrupamentoSerie.MES);
    }

    // ===================== RF02 - distribuição =====================

    @Test
    void distribuicaoPorHoraTrazAs24HorasEOPicoDeVendas() {
        List<ItemDistribuicao> horas = CalculadoraDashboard.distribuicao(cenario, DimensaoDistribuicao.HORA,
                new HashMap<>());

        assertThat(horas).hasSize(24);
        ItemDistribuicao dezenove = horas.get(19);
        assertThat(dezenove.rotulo()).isEqualTo("19h");
        assertThat(dezenove.pedidos()).isEqualTo(2);
        assertThat(dezenove.faturamento()).isEqualByComparingTo("176.70");
        assertThat(dezenove.percentualFaturamento()).isEqualByComparingTo("74.0");
        assertThat(horas.get(12).pedidos()).isZero(); // o pedido das 12h foi cancelado
    }

    @Test
    void distribuicaoPorDiaDaSemanaComecaNaSegunda() {
        List<ItemDistribuicao> dias = CalculadoraDashboard.distribuicao(cenario, DimensaoDistribuicao.DIA_SEMANA,
                new HashMap<>());

        assertThat(dias).extracting(ItemDistribuicao::rotulo)
                .containsExactly("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom");
        assertThat(dias.get(0).pedidos()).isEqualTo(3);
    }

    @Test
    void distribuicaoPorTipoDeEntregaIncluiPedidosSemTipo() {
        Pedido semTipo = pedido(SEGUNDA.atTime(21, 0), StatusPedido.FINALIZADO, null, margherita(1));

        List<ItemDistribuicao> tipos = CalculadoraDashboard.distribuicao(lista(a, b, d, semTipo),
                DimensaoDistribuicao.TIPO_ENTREGA, new HashMap<>());

        assertThat(tipos).extracting(ItemDistribuicao::chave).containsExactly("ENTREGA", "RETIRADA", "NAO_INFORMADO");
        assertThat(tipos.get(0).faturamento()).isEqualByComparingTo("185.81");
        assertThat(tipos.get(2).rotulo()).isEqualTo(CalculadoraDashboard.NAO_INFORMADO);
    }

    @Test
    void distribuicaoPorCategoriaUsaValorLiquidoEOrdenaPeloFaturamento() {
        List<ItemDistribuicao> categorias = CalculadoraDashboard.distribuicao(cenario, DimensaoDistribuicao.CATEGORIA,
                new HashMap<>());

        assertThat(categorias).extracting(ItemDistribuicao::rotulo).containsExactly("Pizza", "Bebida");
        assertThat(categorias.get(0).faturamento()).isEqualByComparingTo("212.11"); // 109,80 + 52,90 + 49,41
        assertThat(categorias.get(0).pedidos()).isEqualTo(3);
        assertThat(categorias.get(0).quantidadeItens()).isEqualTo(4);
        assertThat(categorias.get(0).percentualFaturamento()).isEqualByComparingTo("88.9");
        assertThat(categorias.get(1).faturamento()).isEqualByComparingTo("26.60"); // 14,00 + 12,60
    }

    @Test
    void itemAntigoSemCategoriaUsaOCadastroAtualDoProduto() {
        ItemPedido antigo = item("esfiha", "Esfiha de Carne", null, 4, "7.50");
        ItemPedido desconhecido = item("removido", "Produto removido", null, 1, "10.00");
        Pedido pedido = pedido(SEGUNDA.atTime(19, 0), StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, antigo,
                desconhecido);
        Map<String, String> catalogo = new HashMap<>();
        catalogo.put("esfiha", "Esfiha");

        List<ItemDistribuicao> categorias = CalculadoraDashboard.distribuicao(lista(pedido),
                DimensaoDistribuicao.CATEGORIA, catalogo);

        assertThat(categorias).extracting(ItemDistribuicao::rotulo)
                .containsExactly("Esfiha", CalculadoraDashboard.SEM_CATEGORIA);
    }

    @Test
    void valorLiquidoDoItemAplicaOPercentualDeDescontoDoPedido() {
        assertThat(CalculadoraDashboard.valorLiquidoDoItem(d.getItens().get(0), d)).isEqualByComparingTo("49.41");
        assertThat(CalculadoraDashboard.valorLiquidoDoItem(a.getItens().get(0), a)).isEqualByComparingTo("109.80");
    }

    @Test
    void rateioDoDescontoFechaExatamenteComOTotalDoPedido() {
        // 3 x R$ 3,33 com 10%: total 8,99, mas 2,997 arredondado em cada item daria 9,00
        Pedido pedido = pedidoComDesconto(SEGUNDA.atTime(19, 0), StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, "10",
                item("a", "A", "X", 1, "3.33"), item("b", "B", "X", 1, "3.33"), item("c", "C", "X", 1, "3.33"));

        List<BigDecimal> liquidos = CalculadoraDashboard.valoresLiquidosDosItens(pedido);

        assertThat(pedido.getValorTotal()).isEqualByComparingTo("8.99");
        assertThat(liquidos.get(0).add(liquidos.get(1)).add(liquidos.get(2))).isEqualByComparingTo("8.99");
        assertThat(CalculadoraDashboard.ranking(lista(pedido), new HashMap<>(), null, OrdenacaoRanking.FATURAMENTO,
                10).faturamentoTotal()).isEqualByComparingTo(CalculadoraDashboard.totalizar(lista(pedido)).faturamento());
    }

    // ===================== RF04 - produtos mais vendidos =====================

    @Test
    void rankingPorQuantidadeIgnoraCanceladosECalculaParticipacao() {
        CalculadoraDashboard.Ranking ranking = CalculadoraDashboard.ranking(cenario, new HashMap<>(), null,
                OrdenacaoRanking.QUANTIDADE, 10);

        assertThat(ranking.itens()).extracting(ProdutoRanking::nome)
                .containsExactly("Pizza Calabresa", "Refrigerante 2L", "Pizza Margherita");
        ProdutoRanking primeiro = ranking.itens().get(0);
        assertThat(primeiro.posicao()).isEqualTo(1);
        assertThat(primeiro.quantidade()).isEqualTo(3); // 2 (A) + 1 (D); C cancelado não conta
        assertThat(primeiro.pedidos()).isEqualTo(2);
        assertThat(primeiro.faturamento()).isEqualByComparingTo("159.21");
        assertThat(primeiro.percentualQuantidade()).isEqualByComparingTo("50.0");
        assertThat(primeiro.percentualFaturamento()).isEqualByComparingTo("66.7");
        assertThat(ranking.quantidadeTotal()).isEqualTo(6);
        assertThat(ranking.faturamentoTotal()).isEqualByComparingTo("238.71");
    }

    @Test
    void rankingPorFaturamentoMudaAOrdem() {
        CalculadoraDashboard.Ranking ranking = CalculadoraDashboard.ranking(cenario, new HashMap<>(), null,
                OrdenacaoRanking.FATURAMENTO, 10);

        assertThat(ranking.itens()).extracting(ProdutoRanking::nome)
                .containsExactly("Pizza Calabresa", "Pizza Margherita", "Refrigerante 2L");
    }

    @Test
    void rankingRespeitaLimiteECategoriaSemDiferenciarMaiusculas() {
        CalculadoraDashboard.Ranking limitado = CalculadoraDashboard.ranking(cenario, new HashMap<>(), null,
                OrdenacaoRanking.QUANTIDADE, 1);
        CalculadoraDashboard.Ranking bebidas = CalculadoraDashboard.ranking(cenario, new HashMap<>(), "bebida",
                OrdenacaoRanking.QUANTIDADE, 10);

        assertThat(limitado.itens()).hasSize(1);
        assertThat(limitado.totalProdutos()).isEqualTo(3);
        assertThat(bebidas.itens()).extracting(ProdutoRanking::nome).containsExactly("Refrigerante 2L");
        assertThat(bebidas.itens().get(0).percentualQuantidade()).isEqualByComparingTo("100.0");
    }

    @Test
    void empateNoRankingEhDecididoPeloFaturamentoEDepoisPeloNome() {
        Pedido pedido = pedido(SEGUNDA.atTime(19, 0), StatusPedido.FINALIZADO, TipoEntrega.ENTREGA,
                refrigerante(2), esfiha(2), item("suco", "Suco", "Bebida", 2, "14.00"));

        CalculadoraDashboard.Ranking ranking = CalculadoraDashboard.ranking(lista(pedido), new HashMap<>(), null,
                OrdenacaoRanking.QUANTIDADE, 10);

        assertThat(ranking.itens()).extracting(ProdutoRanking::nome)
                .containsExactly("Refrigerante 2L", "Suco", "Esfiha de Carne");
    }

    // ===================== RF03 - situação dos pedidos =====================

    @Test
    void situacaoTrazTodosOsStatusComQuantidadeEPercentual() {
        CalculadoraDashboard.Situacao situacao = CalculadoraDashboard.situacao(cenario);

        assertThat(situacao.total()).isEqualTo(4);
        assertThat(situacao.porStatus()).hasSize(StatusPedido.values().length);
        QuantidadePorStatus finalizado = buscar(situacao.porStatus(), StatusPedido.FINALIZADO);
        assertThat(finalizado.quantidade()).isEqualTo(1);
        assertThat(finalizado.percentual()).isEqualByComparingTo("25.0");
        assertThat(finalizado.valorTotal()).isEqualByComparingTo("123.80");
        assertThat(buscar(situacao.porStatus(), StatusPedido.PRONTO).quantidade()).isZero();
        assertThat(situacao.emAndamento()).isEqualTo(2);
        assertThat(situacao.finalizados()).isEqualTo(1);
        assertThat(situacao.cancelados()).isEqualTo(1);
    }

    @Test
    void tempoMedioDeAtendimentoIgnoraDatasInconsistentes() {
        a.setDataHoraSaida(a.getDataHoraEntrada().plusMinutes(30));
        a.setDataHoraFinalizacao(a.getDataHoraEntrada().plusMinutes(55));
        b.setDataHoraSaida(b.getDataHoraEntrada().plusMinutes(41));
        d.setDataHoraSaida(d.getDataHoraEntrada().minusMinutes(5)); // inconsistente: saída antes da entrada

        CalculadoraDashboard.Situacao situacao = CalculadoraDashboard.situacao(cenario);

        assertThat(situacao.tempoMedioAteSaidaMinutos()).isEqualByComparingTo("35.5");
        assertThat(situacao.tempoMedioAteFinalizacaoMinutos()).isEqualByComparingTo("55.0");
        assertThat(CalculadoraDashboard.situacao(new ArrayList<>()).tempoMedioAteSaidaMinutos()).isNull();
    }

    @Test
    void filaEmAbertoMostraOsMaisAntigosPrimeiroEMarcaOsAtrasados() {
        LocalDateTime agora = SEGUNDA.atTime(20, 30);
        // A (finalizado) e C (cancelado) não estão em aberto
        CalculadoraDashboard.FilaEmAberto fila = CalculadoraDashboard.pedidosEmAberto(cenario, agora, 45, 10, false);

        assertThat(fila.total()).isEqualTo(2);
        assertThat(fila.pedidos().get(0).id()).isEqualTo(b.getId()); // 50 min esperando
        assertThat(fila.pedidos().get(0).minutosDesdeEntrada()).isEqualTo(50);
        assertThat(fila.pedidos().get(0).atrasado()).isTrue();
        assertThat(fila.pedidos().get(1).atrasado()).isFalse(); // D: 30 min
        assertThat(fila.atrasados()).isEqualTo(1);
    }

    @Test
    void filaEmAbertoLimitaAsLinhasMasContaTodosEMascaraNomeParaAdmin() {
        CalculadoraDashboard.FilaEmAberto fila = CalculadoraDashboard.pedidosEmAberto(cenario,
                SEGUNDA.atTime(20, 30), 45, 1, true);

        assertThat(fila.total()).isEqualTo(2);
        assertThat(fila.pedidos()).hasSize(1);
        assertThat(fila.pedidos().get(0).clienteNome()).isEqualTo("M*** S***");
    }

    @Test
    void resumoDoPedidoCalculaOTempoDeAtendimento() {
        a.setDataHoraSaida(a.getDataHoraEntrada().plusMinutes(30));
        a.setDataHoraFinalizacao(a.getDataHoraEntrada().plusMinutes(52));

        PedidoResumo resumo = CalculadoraDashboard.resumir(a, false);
        PedidoResumo aberto = CalculadoraDashboard.resumir(d, false);

        assertThat(resumo.minutosAtendimento()).isEqualTo(52);
        assertThat(resumo.quantidadeItens()).isEqualTo(3);
        assertThat(resumo.statusDescricao()).isEqualTo("Finalizado");
        assertThat(aberto.minutosAtendimento()).isNull();
    }

    @Test
    void mascaraDoNomeMantemSoAsIniciais() {
        assertThat(CalculadoraDashboard.nomeCliente("Maria Souza", true)).isEqualTo("M*** S***");
        assertThat(CalculadoraDashboard.nomeCliente("Maria Souza", false)).isEqualTo("Maria Souza");
        assertThat(CalculadoraDashboard.nomeCliente(null, true)).isNull();
    }

    // ===================== apoio =====================

    private static List<Pedido> lista(Pedido... pedidos) {
        List<Pedido> lista = new ArrayList<>();
        for (Pedido pedido : pedidos) {
            lista.add(pedido);
        }
        return lista;
    }

    private static QuantidadePorStatus buscar(List<QuantidadePorStatus> lista, StatusPedido status) {
        for (QuantidadePorStatus item : lista) {
            if (item.status() == status) {
                return item;
            }
        }
        throw new AssertionError("Status ausente: " + status);
    }
}
