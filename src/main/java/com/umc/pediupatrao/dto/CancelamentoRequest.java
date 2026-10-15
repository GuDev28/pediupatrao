package com.umc.pediupatrao.dto;

/** Cancelamento de pedido pelo GERENTE: a justificativa é obrigatória. */
public class CancelamentoRequest {

    private String justificativa;

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }
}
