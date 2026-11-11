package com.umc.pediupatrao.dto.dashboard;

/** Critério de ordenação do ranking de produtos mais vendidos (RF04). */
public enum OrdenacaoRanking {

    QUANTIDADE("Quantidade vendida"),
    FATURAMENTO("Faturamento");

    private final String descricao;

    OrdenacaoRanking(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
