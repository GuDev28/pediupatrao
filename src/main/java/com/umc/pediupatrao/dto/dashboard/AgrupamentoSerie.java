package com.umc.pediupatrao.dto.dashboard;

/** Agrupamento da série temporal de vendas (RF02). */
public enum AgrupamentoSerie {

    DIA("Dia"),
    SEMANA("Semana"),
    MES("Mês");

    private final String descricao;

    AgrupamentoSerie(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Agrupamento usado quando o cliente não informa nenhum: mantém o gráfico legível
     * (no máximo ~60 pontos por dia, ~26 por semana).
     */
    public static AgrupamentoSerie automatico(long dias) {
        if (dias <= 62) {
            return DIA;
        }
        if (dias <= 182) {
            return SEMANA;
        }
        return MES;
    }
}
