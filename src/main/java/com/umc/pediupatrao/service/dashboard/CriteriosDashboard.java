package com.umc.pediupatrao.service.dashboard;

import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.Periodo;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RegraNegocioException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Filtros do dashboard já validados e normalizados (RF05).
 *
 * Regras do período:
 * - sem datas: últimos 30 dias, incluindo hoje;
 * - só dataInicio: até hoje (ou só aquele dia, se for uma data futura);
 * - só dataFim: os 30 dias que terminam nessa data;
 * - dataInicio posterior a dataFim ou período maior que 366 dias: erro 422.
 * As datas são inclusivas e se referem à data/hora de ENTRADA do pedido (horário de Brasília).
 */
public record CriteriosDashboard(LocalDate dataInicio, LocalDate dataFim, TipoEntrega tipoEntrega, String atendente) {

    public static final int DIAS_PADRAO = 30;
    public static final int MAXIMO_DIAS = 366;

    public static CriteriosDashboard de(FiltroDashboard filtro, LocalDate hoje) {
        FiltroDashboard f = filtro == null ? new FiltroDashboard() : filtro;
        LocalDate inicio = LeitorParametros.data(f.getDataInicio(), "dataInicio");
        LocalDate fim = LeitorParametros.data(f.getDataFim(), "dataFim");
        TipoEntrega tipo = LeitorParametros.opcao(f.getTipoEntrega(), TipoEntrega.class, "tipoEntrega");
        String atendente = LeitorParametros.limpar(f.getAtendente());

        if (inicio == null && fim == null) {
            fim = hoje;
            inicio = hoje.minusDays(DIAS_PADRAO - 1L);
        } else if (inicio == null) {
            inicio = fim.minusDays(DIAS_PADRAO - 1L);
        } else if (fim == null) {
            fim = inicio.isAfter(hoje) ? inicio : hoje;
        }

        if (inicio.isAfter(fim)) {
            throw new RegraNegocioException("A data inicial (" + inicio + ") não pode ser posterior à data final ("
                    + fim + ").");
        }
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (dias > MAXIMO_DIAS) {
            throw new RegraNegocioException("O período consultado pode ter no máximo " + MAXIMO_DIAS
                    + " dias (informado: " + dias + " dias). Reduza o intervalo de datas.");
        }
        return new CriteriosDashboard(inicio, fim, tipo, atendente);
    }

    public static Periodo periodoPadrao(LocalDate hoje) {
        return new Periodo(hoje.minusDays(DIAS_PADRAO - 1L), hoje, DIAS_PADRAO);
    }

    public long dias() {
        return ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
    }

    public Periodo periodo() {
        return new Periodo(dataInicio, dataFim, dias());
    }

    /** Primeiro instante do período (00:00 da data inicial). */
    public LocalDateTime inicio() {
        return dataInicio.atStartOfDay();
    }

    /** Limite exclusivo do período (00:00 do dia seguinte à data final). */
    public LocalDateTime fimExclusivo() {
        return dataFim.plusDays(1).atStartOfDay();
    }

    /** Período imediatamente anterior, com a mesma quantidade de dias, para comparação. */
    public CriteriosDashboard anterior() {
        long dias = dias();
        return new CriteriosDashboard(dataInicio.minusDays(dias), dataInicio.minusDays(1), tipoEntrega, atendente);
    }
}
