package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDate;

/** Período consultado (datas inclusivas) e sua duração em dias. */
public record Periodo(LocalDate dataInicio, LocalDate dataFim, long dias) {
}
