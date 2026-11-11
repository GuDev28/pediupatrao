package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

/** Valores aceitos pelos filtros do dashboard (RF05), usados para montar a tela. */
public record OpcoesFiltroResponse(
        Periodo periodoPadrao,
        int periodoMaximoDias,
        List<OpcaoFiltro> tiposEntrega,
        List<OpcaoFiltro> status,
        List<String> categorias,
        List<String> atendentes,
        List<OpcaoFiltro> agrupamentos,
        List<OpcaoFiltro> dimensoes,
        List<OpcaoFiltro> ordenacoesRanking) {
}
