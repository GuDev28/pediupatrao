package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Situação dos pedidos registrados no período (RF03): quantidade por status,
 * agrupamentos operacionais e tempos médios de atendimento (em minutos).
 */
public record SituacaoPedidosResponse(
        Periodo periodo,
        long totalPedidos,
        List<QuantidadePorStatus> porStatus,
        long emAndamento,
        long finalizados,
        long cancelados,
        BigDecimal tempoMedioAteSaidaMinutos,
        BigDecimal tempoMedioAteFinalizacaoMinutos,
        LocalDateTime geradoEm) {
}
