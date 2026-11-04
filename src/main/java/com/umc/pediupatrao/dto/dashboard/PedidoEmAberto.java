package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Pedido ainda não finalizado nem cancelado, com o tempo de espera desde a entrada (RF03). */
public record PedidoEmAberto(String id, String codigo, String clienteNome, TipoEntrega tipoEntrega,
        StatusPedido status, String statusDescricao, BigDecimal valorTotal, LocalDateTime dataHoraEntrada,
        Long minutosDesdeEntrada, boolean atrasado) {
}
