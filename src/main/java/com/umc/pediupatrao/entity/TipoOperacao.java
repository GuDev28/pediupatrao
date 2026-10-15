package com.umc.pediupatrao.entity;

/**
 * Tipos de operação registrados na auditoria (RF05).
 */
public enum TipoOperacao {

    // Pedidos
    INCLUSAO_PEDIDO("Inclusão de pedido"),
    ALTERACAO_PEDIDO("Alteração de pedido"),
    ALTERACAO_STATUS_PEDIDO("Alteração de status do pedido"),
    APLICACAO_DESCONTO("Aplicação de desconto"),
    CANCELAMENTO_PEDIDO("Cancelamento de pedido"),

    // Clientes
    INCLUSAO_CLIENTE("Inclusão de cliente"),
    ALTERACAO_CLIENTE("Alteração de cliente"),
    EXCLUSAO_CLIENTE("Exclusão de cliente"),

    // Produtos (informações comerciais)
    INCLUSAO_PRODUTO("Inclusão de produto"),
    ALTERACAO_PRODUTO("Alteração de produto"),
    EXCLUSAO_PRODUTO("Exclusão de produto"),

    // Usuários e perfis
    INCLUSAO_USUARIO("Inclusão de usuário"),
    ALTERACAO_USUARIO("Alteração de usuário"),
    EXCLUSAO_USUARIO("Exclusão de usuário"),

    // Segurança
    ACESSO_NEGADO("Tentativa de acesso não autorizado");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
