package com.umc.pediupatrao.entity;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Set;

/**
 * Fluxo obrigatório dos pedidos (RF03):
 *
 * RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA | RETIRADO -> FINALIZADO
 *
 * CANCELADO é um estado excepcional: só pode ser alcançado pela operação de
 * cancelamento (exclusiva do GERENTE) enquanto o pedido ainda não saiu.
 * FINALIZADO e CANCELADO são estados finais: nenhuma transição sai deles.
 */
public enum StatusPedido {

    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Estados que podem suceder este no fluxo normal (sem considerar o cancelamento,
     * que tem operação própria).
     */
    public Set<StatusPedido> getProximosPermitidos() {
        switch (this) {
            case RECEBIDO:
                return EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO:
                return EnumSet.of(PRONTO);
            case PRONTO:
                return EnumSet.of(SAIU_PARA_ENTREGA, RETIRADO);
            case SAIU_PARA_ENTREGA:
            case RETIRADO:
                return EnumSet.of(FINALIZADO);
            default:
                return EnumSet.noneOf(StatusPedido.class);
        }
    }

    public boolean podeAvancarPara(StatusPedido destino) {
        return destino != null && getProximosPermitidos().contains(destino);
    }

    /** O pedido ainda está no estabelecimento (não saiu para entrega nem foi retirado). */
    public boolean isAntesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    /** Estados que representam a saída do pedido. */
    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    /** Estados finais: não admitem nenhuma alteração posterior. */
    public boolean isFinal() {
        return this == FINALIZADO || this == CANCELADO;
    }

    /**
     * Converte um texto vindo de uma requisição em status, aceitando variações como
     * "em preparação", "EM-PREPARACAO" ou "saiu para entrega".
     *
     * @return o status ou null se o texto não corresponder a nenhum status
     */
    public static StatusPedido deTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String normalizado = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replaceAll("[\\s\\-/]+", "_");
        for (StatusPedido status : values()) {
            if (status.name().equals(normalizado)) {
                return status;
            }
        }
        return null;
    }
}
