package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Um ponto da série temporal: vendas de um dia, semana ou mês. */
public record PontoSerie(LocalDate inicio, LocalDate fim, String rotulo, BigDecimal faturamento, long pedidos,
        BigDecimal ticketMedio) {
}
