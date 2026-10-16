package com.umc.pediupatrao.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PedidoTest {

    private Pedido pedidoComItens() {
        List<ItemPedido> itens = new ArrayList<>();
        itens.add(new ItemPedido("p1", "Pizza Calabresa", 2, new BigDecimal("54.90")));
        itens.add(new ItemPedido("p2", "Refrigerante 2L", 1, new BigDecimal("14.00")));
        Pedido pedido = new Pedido();
        pedido.setItens(itens);
        return pedido;
    }

    @Test
    void calculaSubtotalETotalSemDesconto() {
        Pedido pedido = pedidoComItens();
        pedido.recalcularValores();

        assertThat(pedido.getSubtotal()).isEqualByComparingTo("123.80");
        assertThat(pedido.getValorDesconto()).isEqualByComparingTo("0");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("123.80");
    }

    @Test
    void aplicaPercentualComArredondamentoDeDuasCasas() {
        Pedido pedido = pedidoComItens();
        pedido.setPercentualDesconto(new BigDecimal("12.5"));
        pedido.recalcularValores();

        // 123,80 x 12,5% = 15,475 -> 15,48
        assertThat(pedido.getValorDesconto()).isEqualByComparingTo("15.48");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("108.32");
    }

    @Test
    void proximoStatusDependeDoTipoDeEntrega() {
        Pedido pedido = pedidoComItens();
        pedido.setStatus(StatusPedido.PRONTO);

        pedido.setTipoEntrega(TipoEntrega.ENTREGA);
        assertThat(pedido.getProximoStatus()).isEqualTo(StatusPedido.SAIU_PARA_ENTREGA);

        pedido.setTipoEntrega(TipoEntrega.RETIRADA);
        assertThat(pedido.getProximoStatus()).isEqualTo(StatusPedido.RETIRADO);
    }
}
