package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Indicadores gerais de vendas (RF01).
 *
 * Faturamento, pedidos, ticket médio e itens consideram apenas pedidos válidos
 * (todos, exceto os CANCELADOS). Cancelamentos e descontos são informados à parte.
 */
public record ResumoVendasResponse(
        Periodo periodo,
        Periodo periodoAnterior,
        Indicador faturamento,
        Indicador pedidos,
        Indicador ticketMedio,
        Indicador itensVendidos,
        long pedidosCancelados,
        BigDecimal taxaCancelamento,
        BigDecimal valorCancelado,
        long pedidosComDesconto,
        BigDecimal valorDescontos,
        long pedidosEmAndamento,
        LocalDateTime geradoEm) {
}
