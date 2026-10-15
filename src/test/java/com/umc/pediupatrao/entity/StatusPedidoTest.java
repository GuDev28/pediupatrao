package com.umc.pediupatrao.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatusPedidoTest {

    @Test
    void fluxoNormalPermiteApenasOProximoPasso() {
        assertThat(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.EM_PREPARACAO)).isTrue();
        assertThat(StatusPedido.EM_PREPARACAO.podeAvancarPara(StatusPedido.PRONTO)).isTrue();
        assertThat(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.SAIU_PARA_ENTREGA)).isTrue();
        assertThat(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.RETIRADO)).isTrue();
        assertThat(StatusPedido.SAIU_PARA_ENTREGA.podeAvancarPara(StatusPedido.FINALIZADO)).isTrue();
        assertThat(StatusPedido.RETIRADO.podeAvancarPara(StatusPedido.FINALIZADO)).isTrue();
    }

    @Test
    void bloqueiaSaltosRetrocessosECancelamentoPeloFluxoNormal() {
        assertThat(StatusPedido.EM_PREPARACAO.podeAvancarPara(StatusPedido.FINALIZADO)).isFalse();
        assertThat(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.PRONTO)).isFalse();
        assertThat(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.RECEBIDO)).isFalse();
        assertThat(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.CANCELADO)).isFalse();
    }

    @Test
    void estadosFinaisNaoTemSaida() {
        assertThat(StatusPedido.FINALIZADO.getProximosPermitidos()).isEmpty();
        assertThat(StatusPedido.CANCELADO.getProximosPermitidos()).isEmpty();
        assertThat(StatusPedido.CANCELADO.isFinal()).isTrue();
    }

    @Test
    void identificaEstadosAntesDaSaida() {
        assertThat(StatusPedido.PRONTO.isAntesDaSaida()).isTrue();
        assertThat(StatusPedido.SAIU_PARA_ENTREGA.isAntesDaSaida()).isFalse();
        assertThat(StatusPedido.RETIRADO.isAntesDaSaida()).isFalse();
    }

    @Test
    void converteTextoComAcentosEspacosEMinusculas() {
        assertThat(StatusPedido.deTexto("em preparação")).isEqualTo(StatusPedido.EM_PREPARACAO);
        assertThat(StatusPedido.deTexto("SAIU PARA ENTREGA")).isEqualTo(StatusPedido.SAIU_PARA_ENTREGA);
        assertThat(StatusPedido.deTexto("pronto")).isEqualTo(StatusPedido.PRONTO);
        assertThat(StatusPedido.deTexto("ENTREGUE")).isNull();
        assertThat(StatusPedido.deTexto(" ")).isNull();
    }
}
