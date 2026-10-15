package com.umc.pediupatrao.dto;

/**
 * Item informado pelo usuário: apenas o produto e a quantidade.
 * O preço é sempre buscado no cadastro de produtos pelo servidor.
 */
public class ItemPedidoRequest {

    private String produtoId;
    private Integer quantidade;

    public ItemPedidoRequest() {
    }

    public ItemPedidoRequest(String produtoId, Integer quantidade) {
        this.produtoId = produtoId;
        this.quantidade = quantidade;
    }

    public String getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(String produtoId) {
        this.produtoId = produtoId;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }
}
