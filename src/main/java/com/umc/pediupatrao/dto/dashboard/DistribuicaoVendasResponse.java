package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Distribuição das vendas por hora, dia da semana, tipo de entrega ou categoria (RF02). */
public record DistribuicaoVendasResponse(
        Periodo periodo,
        DimensaoDistribuicao dimensao,
        List<ItemDistribuicao> itens,
        BigDecimal totalFaturamento,
        LocalDateTime geradoEm) {
}
