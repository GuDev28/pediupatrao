package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;

/** Quantidade e valor dos pedidos de um status no período (RF03). */
public record QuantidadePorStatus(StatusPedido status, String descricao, long quantidade, BigDecimal percentual,
        BigDecimal valorTotal) {
}
