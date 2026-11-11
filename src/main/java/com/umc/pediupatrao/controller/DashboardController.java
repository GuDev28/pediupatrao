package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.PaginaResposta;
import com.umc.pediupatrao.dto.dashboard.DistribuicaoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltroResponse;
import com.umc.pediupatrao.dto.dashboard.PedidoResumo;
import com.umc.pediupatrao.dto.dashboard.PedidosEmAbertoResponse;
import com.umc.pediupatrao.dto.dashboard.RankingProdutosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SerieVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidosResponse;
import com.umc.pediupatrao.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API do dashboard administrativo (somente leitura; ADMIN e GERENTE).
 *
 * Filtros comuns (query string, todos opcionais): dataInicio, dataFim (aaaa-mm-dd),
 * tipoEntrega (ENTREGA | RETIRADA) e atendente. A documentação completa, com exemplos
 * e erros, está em docs/dashboard/API-DASHBOARD.md.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Opções dos filtros (tipos, status, categorias, atendentes) e período padrão. */
    @GetMapping("/filtros")
    public OpcoesFiltroResponse filtros() {
        return dashboardService.opcoesDeFiltro();
    }

    /** RF01 - faturamento, pedidos, ticket médio, itens, cancelamentos e descontos, com comparação. */
    @GetMapping("/resumo")
    public ResumoVendasResponse resumo(FiltroDashboard filtro) {
        return dashboardService.resumo(filtro);
    }

    /** RF02 - evolução das vendas por DIA, SEMANA ou MES (automático se omitido). */
    @GetMapping("/vendas/serie")
    public SerieVendasResponse serie(FiltroDashboard filtro,
            @RequestParam(required = false) String agrupamento) {
        return dashboardService.serieDeVendas(filtro, agrupamento);
    }

    /** RF02 - distribuição por HORA, DIA_SEMANA, TIPO_ENTREGA ou CATEGORIA. */
    @GetMapping("/vendas/distribuicao")
    public DistribuicaoVendasResponse distribuicao(FiltroDashboard filtro,
            @RequestParam(required = false) String dimensao) {
        return dashboardService.distribuicao(filtro, dimensao);
    }

    /** RF04 - ranking de produtos por QUANTIDADE ou FATURAMENTO, com filtro de categoria. */
    @GetMapping("/produtos/mais-vendidos")
    public RankingProdutosResponse maisVendidos(FiltroDashboard filtro,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String ordenarPor,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.produtosMaisVendidos(filtro, categoria, ordenarPor, limite);
    }

    /** RF03 - quantidade por status e tempos médios dos pedidos do período. */
    @GetMapping("/pedidos/situacao")
    public SituacaoPedidosResponse situacao(FiltroDashboard filtro) {
        return dashboardService.situacaoDosPedidos(filtro);
    }

    /** RF03 - fila de pedidos em aberto agora, destacando os atrasados. */
    @GetMapping("/pedidos/em-aberto")
    public PedidosEmAbertoResponse emAberto(@RequestParam(required = false) String tipoEntrega,
            @RequestParam(required = false) Integer limiteMinutos,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.pedidosEmAberto(tipoEntrega, limiteMinutos, limite);
    }

    /** RF03/RF05 - pedidos do período, paginados, com filtro de status. */
    @GetMapping("/pedidos")
    public PaginaResposta<PedidoResumo> pedidos(FiltroDashboard filtro,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho) {
        return dashboardService.listarPedidos(filtro, status, pagina, tamanho);
    }
}
