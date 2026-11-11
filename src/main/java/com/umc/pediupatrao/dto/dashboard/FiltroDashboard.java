package com.umc.pediupatrao.dto.dashboard;

/**
 * Filtros comuns aos endpoints do dashboard (RF05), recebidos como parâmetros da URL.
 *
 * Os campos são texto de propósito: a conversão e a validação ficam no servidor
 * (CriteriosDashboard), que devolve mensagens claras em vez de erros genéricos de conversão.
 *
 * - dataInicio / dataFim: datas no formato yyyy-MM-dd (inclusivas). Padrão: últimos 30 dias.
 * - tipoEntrega: ENTREGA ou RETIRADA (opcional).
 * - atendente: usuário que registrou o pedido (opcional).
 */
public class FiltroDashboard {

    private String dataInicio;
    private String dataFim;
    private String tipoEntrega;
    private String atendente;

    public FiltroDashboard() {
    }

    public FiltroDashboard(String dataInicio, String dataFim, String tipoEntrega, String atendente) {
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.tipoEntrega = tipoEntrega;
        this.atendente = atendente;
    }

    public String getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(String dataInicio) {
        this.dataInicio = dataInicio;
    }

    public String getDataFim() {
        return dataFim;
    }

    public void setDataFim(String dataFim) {
        this.dataFim = dataFim;
    }

    public String getTipoEntrega() {
        return tipoEntrega;
    }

    public void setTipoEntrega(String tipoEntrega) {
        this.tipoEntrega = tipoEntrega;
    }

    public String getAtendente() {
        return atendente;
    }

    public void setAtendente(String atendente) {
        this.atendente = atendente;
    }
}
