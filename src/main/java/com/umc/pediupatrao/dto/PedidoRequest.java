package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.TipoEntrega;
import java.util.ArrayList;
import java.util.List;

/**
 * Dados aceitos para registrar ou alterar um pedido (API JSON e formulário).
 *
 * Propositalmente NÃO contém preços, subtotal, desconto, total, status, datas ou
 * responsáveis: esses valores são definidos exclusivamente pelo servidor.
 * Campos extras enviados na requisição (ex.: "valorTotal") são ignorados.
 */
public class PedidoRequest {

    private String clienteId;
    private TipoEntrega tipoEntrega;
    private String observacao;
    private List<ItemPedidoRequest> itens = new ArrayList<>();

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public TipoEntrega getTipoEntrega() {
        return tipoEntrega;
    }

    public void setTipoEntrega(TipoEntrega tipoEntrega) {
        this.tipoEntrega = tipoEntrega;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public List<ItemPedidoRequest> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedidoRequest> itens) {
        this.itens = itens;
    }
}
