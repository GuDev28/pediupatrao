package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/** Posição de um produto no ranking de mais vendidos (RF04). */
public record ProdutoRanking(int posicao, String produtoId, String nome, String categoria, long quantidade,
        BigDecimal faturamento, long pedidos, BigDecimal percentualQuantidade, BigDecimal percentualFaturamento) {
}
