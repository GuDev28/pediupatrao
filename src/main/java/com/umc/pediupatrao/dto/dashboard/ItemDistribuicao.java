package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/** Uma fatia da distribuição das vendas (ex.: 19h, Sexta, ENTREGA ou Pizza). */
public record ItemDistribuicao(String chave, String rotulo, long pedidos, long quantidadeItens,
        BigDecimal faturamento, BigDecimal percentualFaturamento) {
}
