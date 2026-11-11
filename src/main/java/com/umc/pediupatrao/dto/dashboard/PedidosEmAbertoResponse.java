package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fila de pedidos em aberto no momento da consulta (tempo real; não usa o filtro de período).
 * "total" e "atrasados" consideram todos os pedidos em aberto; "pedidos" traz no máximo "limite" itens,
 * do que espera há mais tempo para o mais recente.
 */
public record PedidosEmAbertoResponse(int limiteMinutos, long total, long atrasados, List<PedidoEmAberto> pedidos,
        LocalDateTime geradoEm) {
}
