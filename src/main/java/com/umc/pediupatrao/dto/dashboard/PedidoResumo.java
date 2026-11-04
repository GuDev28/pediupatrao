package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Linha da lista de pedidos do dashboard (RF03/RF05): apenas os dados necessários para a
 * visão gerencial, sem histórico, observações ou justificativas.
 */
public record PedidoResumo(String id, String codigo, String clienteNome, TipoEntrega tipoEntrega,
        StatusPedido status, String statusDescricao, int quantidadeItens, BigDecimal valorTotal,
        BigDecimal percentualDesconto, LocalDateTime dataHoraEntrada, LocalDateTime dataHoraSaida,
        String registradoPor, Long minutosAtendimento) {
}
