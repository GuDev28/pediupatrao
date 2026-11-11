package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Produtos mais vendidos no período (RF04). */
public record RankingProdutosResponse(
        Periodo periodo,
        OrdenacaoRanking ordenarPor,
        String categoria,
        int limite,
        int totalProdutos,
        long quantidadeTotal,
        BigDecimal faturamentoTotal,
        List<ProdutoRanking> itens,
        LocalDateTime geradoEm) {
}
