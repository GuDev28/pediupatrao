package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

/**
 * Registro de cada mudança de status do pedido, guardado dentro do próprio pedido
 * para exibir a linha do tempo (entrada, preparo, saída, finalização ou cancelamento).
 */
public class HistoricoStatus {

    private StatusPedido statusAnterior;
    private StatusPedido statusNovo;
    private LocalDateTime dataHora;
    private String usuario;
    private String perfil;

    public HistoricoStatus() {
    }

    public HistoricoStatus(StatusPedido statusAnterior, StatusPedido statusNovo,
            LocalDateTime dataHora, String usuario, String perfil) {
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
    }

    public StatusPedido getStatusAnterior() {
        return statusAnterior;
    }

    public StatusPedido getStatusNovo() {
        return statusNovo;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getPerfil() {
        return perfil;
    }
}
