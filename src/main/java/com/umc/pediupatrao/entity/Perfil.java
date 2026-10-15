package com.umc.pediupatrao.entity;

/**
 * Perfis de acesso do sistema (RF01).
 *
 * ADMIN     - administra usuários e perfis, consulta pedidos e visualiza a auditoria.
 * GERENTE   - gerencia pedidos, aplica descontos, cancela pedidos, consulta clientes e visualiza a auditoria.
 * ATENDENTE - cadastra e atualiza clientes, registra pedidos e acompanha o andamento.
 *
 * No Spring Security cada perfil vira a autoridade "ROLE_" + nome (ex.: ROLE_GERENTE).
 */
public enum Perfil {

    ADMIN("Administrador"),
    GERENTE("Gerente"),
    ATENDENTE("Atendente");

    private final String descricao;

    Perfil(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getAutoridade() {
        return "ROLE_" + name();
    }
}
