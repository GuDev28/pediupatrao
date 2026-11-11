package com.umc.pediupatrao.dto.dashboard;

/** Dimensões disponíveis para a distribuição das vendas (RF02). */
public enum DimensaoDistribuicao {

    HORA("Hora do dia"),
    DIA_SEMANA("Dia da semana"),
    TIPO_ENTREGA("Tipo de entrega"),
    CATEGORIA("Categoria de produto");

    private final String descricao;

    DimensaoDistribuicao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
