package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

/** Desconto percentual (0 a 20%) aplicado pelo GERENTE. */
public class DescontoRequest {

    private BigDecimal percentual;
    private String justificativa;

    public BigDecimal getPercentual() {
        return percentual;
    }

    public void setPercentual(BigDecimal percentual) {
        this.percentual = percentual;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }
}
