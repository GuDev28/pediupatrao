package com.umc.pediupatrao.service.dashboard;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Montagem de pedidos em memória para os testes do dashboard (sem MongoDB). */
public final class PedidosDeTeste {

    private static int sequencia = 0;

    private PedidosDeTeste() {
    }

    public static ItemPedido item(String produtoId, String nome, String categoria, int quantidade, String preco) {
        ItemPedido item = new ItemPedido(produtoId, nome, quantidade, new BigDecimal(preco));
        item.setCategoria(categoria);
        return item;
    }

    public static Pedido pedido(LocalDateTime entrada, StatusPedido status, TipoEntrega tipo, ItemPedido... itens) {
        return pedidoComDesconto(entrada, status, tipo, "0", itens);
    }

    public static Pedido pedidoComDesconto(LocalDateTime entrada, StatusPedido status, TipoEntrega tipo,
            String percentualDesconto, ItemPedido... itens) {
        Pedido pedido = new Pedido();
        sequencia++;
        pedido.setId(String.format("6650a1b2c3d4e5f6a7%06d", sequencia));
        pedido.setClienteId("cli" + sequencia);
        pedido.setClienteNome("Maria Souza");
        pedido.setTipoEntrega(tipo);
        List<ItemPedido> lista = new ArrayList<>();
        for (ItemPedido item : itens) {
            lista.add(item);
        }
        pedido.setItens(lista);
        pedido.setPercentualDesconto(new BigDecimal(percentualDesconto));
        pedido.recalcularValores();
        pedido.setStatus(status);
        pedido.setDataHoraEntrada(entrada);
        pedido.setRegistradoPor("atendente01");
        return pedido;
    }

    public static ItemPedido calabresa(int quantidade) {
        return item("calabresa", "Pizza Calabresa", "Pizza", quantidade, "54.90");
    }

    public static ItemPedido margherita(int quantidade) {
        return item("margherita", "Pizza Margherita", "Pizza", quantidade, "52.90");
    }

    public static ItemPedido refrigerante(int quantidade) {
        return item("refri", "Refrigerante 2L", "Bebida", quantidade, "14.00");
    }

    public static ItemPedido esfiha(int quantidade) {
        return item("esfiha", "Esfiha de Carne", "Esfiha", quantidade, "7.50");
    }
}
