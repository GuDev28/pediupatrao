package com.umc.pediupatrao.entity;

/**
 * Forma de saída do pedido: entrega no endereço do cliente ou retirada no balcão.
 * Define qual status de saída é válido a partir de PRONTO.
 */
public enum TipoEntrega {

    ENTREGA("Entrega", StatusPedido.SAIU_PARA_ENTREGA),
    RETIRADA("Retirada no balcão", StatusPedido.RETIRADO);

    private final String descricao;
    private final StatusPedido statusDeSaida;

    TipoEntrega(String descricao, StatusPedido statusDeSaida) {
        this.descricao = descricao;
        this.statusDeSaida = statusDeSaida;
    }

    public String getDescricao() {
        return descricao;
    }

    public StatusPedido getStatusDeSaida() {
        return statusDeSaida;
    }
}
