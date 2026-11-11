package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/**
 * Valor de um indicador no período consultado e no período anterior de mesma duração.
 * variacaoPercentual é null quando o período anterior é zero (não há base de comparação).
 */
public record Indicador(BigDecimal atual, BigDecimal anterior, BigDecimal variacaoPercentual) {
}
