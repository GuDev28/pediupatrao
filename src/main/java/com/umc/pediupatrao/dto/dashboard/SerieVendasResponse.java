package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Evolução das vendas no período (RF02). Dias/semanas/meses sem vendas aparecem com zero. */
public record SerieVendasResponse(
        Periodo periodo,
        AgrupamentoSerie agrupamento,
        List<PontoSerie> pontos,
        BigDecimal totalFaturamento,
        long totalPedidos,
        LocalDateTime geradoEm) {
}
