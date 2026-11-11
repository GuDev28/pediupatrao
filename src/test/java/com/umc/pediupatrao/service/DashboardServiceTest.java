package com.umc.pediupatrao.service;

import static com.umc.pediupatrao.service.dashboard.PedidosDeTeste.calabresa;
import static com.umc.pediupatrao.service.dashboard.PedidosDeTeste.item;
import static com.umc.pediupatrao.service.dashboard.PedidosDeTeste.margherita;
import static com.umc.pediupatrao.service.dashboard.PedidosDeTeste.pedido;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.PaginaResposta;
import com.umc.pediupatrao.dto.dashboard.AgrupamentoSerie;
import com.umc.pediupatrao.dto.dashboard.DimensaoDistribuicao;
import com.umc.pediupatrao.dto.dashboard.DistribuicaoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltroResponse;
import com.umc.pediupatrao.dto.dashboard.OrdenacaoRanking;
import com.umc.pediupatrao.dto.dashboard.PedidoResumo;
import com.umc.pediupatrao.dto.dashboard.PedidosEmAbertoResponse;
import com.umc.pediupatrao.dto.dashboard.RankingProdutosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SerieVendasResponse;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.PedidoDashboardRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Orquestração do DashboardService: período enviado ao repositório, comparação com o
 * período anterior, valores padrão, validação dos parâmetros e minimização de dados.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    // 03/11/2026 16:30 no horário de Brasília
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T19:30:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final LocalDateTime agora = LocalDateTime.of(2026, 11, 3, 16, 30);

    @Mock
    private PedidoDashboardRepository repository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private UsuarioLogado usuarioLogado;

    private DashboardService service;

    @BeforeEach
    void configurar() {
        service = new DashboardService(repository, produtoRepository, usuarioLogado, clock);
        lenient().when(usuarioLogado.possuiPerfil(Perfil.ADMIN)).thenReturn(false);
        lenient().when(produtoRepository.findAll()).thenReturn(new ArrayList<>());
    }

    @Test
    void resumoConsultaOPeriodoPadraoEOPeriodoAnteriorNoBanco() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 5, 0, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 11, 4, 0, 0);
        LocalDateTime inicioAnterior = LocalDateTime.of(2026, 9, 5, 0, 0);
        when(repository.buscarPorPeriodo(inicio, fim, null, null)).thenReturn(lista(
                pedido(agora.minusDays(1), StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, calabresa(2))));
        when(repository.buscarPorPeriodo(inicioAnterior, inicio, null, null)).thenReturn(lista(
                pedido(agora.minusDays(40), StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, calabresa(1))));

        ResumoVendasResponse resumo = service.resumo(new FiltroDashboard());

        assertThat(resumo.periodo().dataInicio()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(resumo.periodo().dias()).isEqualTo(30);
        assertThat(resumo.periodoAnterior().dataFim()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(resumo.faturamento().atual()).isEqualByComparingTo("109.80");
        assertThat(resumo.faturamento().anterior()).isEqualByComparingTo("54.90");
        assertThat(resumo.faturamento().variacaoPercentual()).isEqualByComparingTo("100.0");
        assertThat(resumo.pedidos().variacaoPercentual()).isEqualByComparingTo("0.0");
        assertThat(resumo.geradoEm()).isEqualTo(agora);
    }

    @Test
    void filtrosDeTipoEAtendenteSaoRepassadosParaAConsulta() {
        FiltroDashboard filtro = new FiltroDashboard("2026-11-01", "2026-11-03", "retirada", "gerente01");
        when(repository.buscarPorPeriodo(any(), any(), eq(TipoEntrega.RETIRADA), eq("gerente01")))
                .thenReturn(new ArrayList<>());

        service.situacaoDosPedidos(filtro);

        verify(repository).buscarPorPeriodo(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 4, 0, 0),
                TipoEntrega.RETIRADA, "gerente01");
    }

    @Test
    void periodoInvalidoNaoChegaAoBanco() {
        assertThatThrownBy(() -> service.resumo(new FiltroDashboard("2026-11-03", "2026-10-01", null, null)))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.resumo(new FiltroDashboard("ontem", null, null, null)))
                .isInstanceOf(ParametroInvalidoException.class);
        verify(repository, never()).buscarPorPeriodo(any(), any(), any(), any());
    }

    @Test
    void serieEscolheOAgrupamentoAutomaticamenteOuUsaOInformado() {
        when(repository.buscarPorPeriodo(any(), any(), isNull(), isNull())).thenReturn(new ArrayList<>());

        SerieVendasResponse automatica = service.serieDeVendas(new FiltroDashboard(), null);
        SerieVendasResponse semanal = service.serieDeVendas(new FiltroDashboard(), "semana");

        assertThat(automatica.agrupamento()).isEqualTo(AgrupamentoSerie.DIA);
        assertThat(automatica.pontos()).hasSize(30);
        assertThat(semanal.agrupamento()).isEqualTo(AgrupamentoSerie.SEMANA);
        assertThatThrownBy(() -> service.serieDeVendas(new FiltroDashboard(), "ANO"))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("DIA, SEMANA, MES");
    }

    @Test
    void distribuicaoPorCategoriaUsaOCadastroDeProdutosParaItensAntigos() {
        Produto esfiha = new Produto();
        esfiha.setId("esfiha");
        esfiha.setTipo("Esfiha");
        when(produtoRepository.findAll()).thenReturn(List.of(esfiha));
        when(repository.buscarPorPeriodo(any(), any(), isNull(), isNull())).thenReturn(lista(
                pedido(agora.minusHours(2), StatusPedido.FINALIZADO, TipoEntrega.ENTREGA,
                        item("esfiha", "Esfiha de Carne", null, 6, "7.50"))));

        DistribuicaoVendasResponse resposta = service.distribuicao(new FiltroDashboard(), "CATEGORIA");

        assertThat(resposta.dimensao()).isEqualTo(DimensaoDistribuicao.CATEGORIA);
        assertThat(resposta.itens().get(0).rotulo()).isEqualTo("Esfiha");
        assertThat(resposta.totalFaturamento()).isEqualByComparingTo("45.00");
        assertThat(service.distribuicao(new FiltroDashboard(), null).dimensao()).isEqualTo(DimensaoDistribuicao.HORA);
    }

    @Test
    void rankingUsaPadroesEValidaOLimite() {
        when(repository.buscarPorPeriodo(any(), any(), isNull(), isNull())).thenReturn(lista(
                pedido(agora.minusHours(3), StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, calabresa(2), margherita(1))));

        RankingProdutosResponse ranking = service.produtosMaisVendidos(new FiltroDashboard(), " ", null, null);

        assertThat(ranking.ordenarPor()).isEqualTo(OrdenacaoRanking.QUANTIDADE);
        assertThat(ranking.limite()).isEqualTo(DashboardService.LIMITE_RANKING_PADRAO);
        assertThat(ranking.categoria()).isNull();
        assertThat(ranking.itens()).hasSize(2);
        assertThatThrownBy(() -> service.produtosMaisVendidos(new FiltroDashboard(), null, null, 51))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void filaEmAbertoNaoUsaOPeriodoEMascaraNomesParaOAdmin() {
        when(usuarioLogado.possuiPerfil(Perfil.ADMIN)).thenReturn(true);
        when(repository.buscarEmAberto(TipoEntrega.ENTREGA)).thenReturn(lista(
                pedido(agora.minusMinutes(70), StatusPedido.PRONTO, TipoEntrega.ENTREGA, calabresa(1))));

        PedidosEmAbertoResponse fila = service.pedidosEmAberto("ENTREGA", null, null);

        assertThat(fila.limiteMinutos()).isEqualTo(DashboardService.MINUTOS_ATRASO_PADRAO);
        assertThat(fila.atrasados()).isEqualTo(1);
        assertThat(fila.pedidos().get(0).minutosDesdeEntrada()).isEqualTo(70);
        assertThat(fila.pedidos().get(0).clienteNome()).isEqualTo("M*** S***");
        verify(repository, never()).buscarPorPeriodo(any(), any(), any(), any());
    }

    @Test
    void listaDePedidosPaginadaComFiltroDeStatus() {
        List<Pedido> conteudo = lista(pedido(agora.minusHours(1), StatusPedido.CANCELADO, TipoEntrega.ENTREGA,
                calabresa(1)));
        when(repository.buscarPagina(any(), any(), isNull(), isNull(), eq(StatusPedido.CANCELADO), eq(0), eq(5)))
                .thenReturn(new PageImpl<>(conteudo, PageRequest.of(0, 5), 1));

        PaginaResposta<PedidoResumo> pagina = service.listarPedidos(new FiltroDashboard(), "cancelado", null, 5);

        assertThat(pagina.totalElementos()).isEqualTo(1);
        assertThat(pagina.conteudo().get(0).clienteNome()).isEqualTo("Maria Souza"); // GERENTE vê o nome
        assertThatThrownBy(() -> service.listarPedidos(new FiltroDashboard(), null, null, 500))
                .isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> service.listarPedidos(new FiltroDashboard(), null, -1, null))
                .isInstanceOf(ParametroInvalidoException.class);
        verify(repository, never()).buscarPagina(any(), any(), any(), any(), any(), eq(-1), anyInt());
    }

    @Test
    void opcoesDeFiltroTrazemCategoriasSemRepeticaoEOPeriodoPadrao() {
        Produto pizza1 = produto("p1", "Pizza");
        Produto pizza2 = produto("p2", "Pizza");
        Produto bebida = produto("b1", "Bebida");
        when(produtoRepository.findAll()).thenReturn(List.of(pizza1, pizza2, bebida));
        when(repository.listarAtendentes()).thenReturn(List.of("atendente01", "gerente01"));

        OpcoesFiltroResponse opcoes = service.opcoesDeFiltro();

        assertThat(opcoes.categorias()).containsExactly("Bebida", "Pizza");
        assertThat(opcoes.atendentes()).containsExactly("atendente01", "gerente01");
        assertThat(opcoes.periodoPadrao().dataFim()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(opcoes.periodoMaximoDias()).isEqualTo(366);
        assertThat(opcoes.status()).hasSize(StatusPedido.values().length);
    }

    private static Produto produto(String id, String tipo) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setTipo(tipo);
        return produto;
    }

    private static List<Pedido> lista(Pedido... pedidos) {
        List<Pedido> lista = new ArrayList<>();
        for (Pedido pedido : pedidos) {
            lista.add(pedido);
        }
        return lista;
    }
}
