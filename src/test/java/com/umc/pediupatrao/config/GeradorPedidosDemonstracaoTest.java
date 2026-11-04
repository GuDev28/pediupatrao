package com.umc.pediupatrao.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Os pedidos de demonstração precisam respeitar as mesmas regras dos pedidos reais. */
class GeradorPedidosDemonstracaoTest {

    private final LocalDateTime agora = LocalDateTime.of(2026, 11, 3, 20, 15);

    @Test
    void geraPedidosConsistentesComOFluxoDeStatus() {
        List<Pedido> pedidos = new GeradorPedidosDemonstracao(2026).gerar(60, agora, produtos(), clientes());

        assertThat(pedidos).hasSizeGreaterThan(600);
        for (Pedido pedido : pedidos) {
            assertThat(pedido.getDataHoraEntrada()).isBeforeOrEqualTo(agora);
            assertThat(pedido.getItens()).isNotEmpty();
            assertThat(pedido.getValorTotal()).isPositive();
            assertThat(pedido.getPercentualDesconto()).isLessThanOrEqualTo(new BigDecimal("20"));
            assertThat(pedido.getItens().get(0).getCategoria()).isNotBlank();

            List<HistoricoStatus> historico = pedido.getHistorico();
            assertThat(historico.get(0).getStatusNovo()).isEqualTo(StatusPedido.RECEBIDO);
            assertThat(historico.get(historico.size() - 1).getStatusNovo()).isEqualTo(pedido.getStatus());
            for (int i = 1; i < historico.size(); i++) {
                StatusPedido anterior = historico.get(i).getStatusAnterior();
                StatusPedido novo = historico.get(i).getStatusNovo();
                boolean transicaoValida = anterior.podeAvancarPara(novo)
                        || (novo == StatusPedido.CANCELADO && anterior.isAntesDaSaida());
                assertThat(transicaoValida).as(anterior + " -> " + novo).isTrue();
                assertThat(historico.get(i).getDataHora()).isAfter(historico.get(i - 1).getDataHora());
            }
            if (pedido.getStatus().isSaida() || pedido.getStatus() == StatusPedido.FINALIZADO) {
                assertThat(pedido.getDataHoraSaida()).isNotNull();
                assertThat(pedido.getResponsavelSaida()).isNotNull();
                assertThat(pedido.getStatus() == StatusPedido.FINALIZADO
                        || pedido.getTipoEntrega().getStatusDeSaida() == pedido.getStatus()).isTrue();
            }
            if (pedido.getStatus() == StatusPedido.CANCELADO) {
                assertThat(pedido.getJustificativaCancelamento()).hasSizeGreaterThanOrEqualTo(10);
                assertThat(pedido.getDataHoraSaida()).isNull();
            }
        }
    }

    @Test
    void temCancelamentosDescontosEPedidosEmAndamento() {
        List<Pedido> pedidos = new GeradorPedidosDemonstracao(2026).gerar(60, agora, produtos(), clientes());

        int cancelados = 0;
        int comDesconto = 0;
        int emAndamento = 0;
        for (Pedido pedido : pedidos) {
            if (pedido.getStatus() == StatusPedido.CANCELADO) {
                cancelados++;
            }
            if (pedido.isPossuiDesconto()) {
                comDesconto++;
            }
            if (!pedido.getStatus().isFinal()) {
                emAndamento++;
            }
        }
        assertThat(cancelados).isPositive();
        assertThat(comDesconto).isPositive();
        assertThat(emAndamento).isPositive();
    }

    @Test
    void sempreHaPedidosRecentesEmAbertoParaAFila() {
        LocalDateTime madrugada = LocalDateTime.of(2026, 11, 3, 3, 0); // fora do horário de pico
        List<Pedido> pedidos = new GeradorPedidosDemonstracao(2026).gerar(5, madrugada, produtos(), clientes());

        int emAberto = 0;
        for (Pedido pedido : pedidos) {
            if (!pedido.getStatus().isFinal() && pedido.getDataHoraEntrada().isAfter(madrugada.minusHours(2))) {
                emAberto++;
            }
        }
        assertThat(emAberto).isGreaterThanOrEqualTo(3);
    }

    @Test
    void mesmaSementeGeraOMesmoHistorico() {
        List<Pedido> primeiro = new GeradorPedidosDemonstracao(7).gerar(10, agora, produtos(), clientes());
        List<Pedido> segundo = new GeradorPedidosDemonstracao(7).gerar(10, agora, produtos(), clientes());

        assertThat(primeiro).hasSameSizeAs(segundo);
        assertThat(primeiro.get(0).getValorTotal()).isEqualByComparingTo(segundo.get(0).getValorTotal());
        assertThat(primeiro.get(0).getDataHoraEntrada()).isEqualTo(segundo.get(0).getDataHoraEntrada());
    }

    @Test
    void semProdutosOuClientesNaoGeraNada() {
        GeradorPedidosDemonstracao gerador = new GeradorPedidosDemonstracao(1);

        assertThat(gerador.gerar(30, agora, new ArrayList<>(), clientes())).isEmpty();
        assertThat(gerador.gerar(30, agora, produtos(), new ArrayList<>())).isEmpty();
    }

    private static List<Produto> produtos() {
        List<Produto> produtos = new ArrayList<>();
        produtos.add(produto("p1", "Pizza Calabresa", "Pizza", 54.90));
        produtos.add(produto("p2", "Pizza Margherita", "Pizza", 52.90));
        produtos.add(produto("e1", "Esfiha de Carne", "Esfiha", 7.50));
        produtos.add(produto("b1", "Refrigerante 2L", "Bebida", 14.00));
        return produtos;
    }

    private static Produto produto(String id, String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }

    private static List<Cliente> clientes() {
        List<Cliente> clientes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Cliente cliente = new Cliente();
            cliente.setId("c" + i);
            cliente.setNome("Cliente " + i);
            clientes.add(cliente);
        }
        return clientes;
    }
}
