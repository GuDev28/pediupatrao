package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gera um histórico realista de pedidos para demonstrar o dashboard (perfil "demo").
 *
 * - Mais movimento de sexta a domingo e nos horários de almoço e jantar (pico às 20h).
 * - Leve crescimento ao longo do período, para a comparação com o período anterior ter sentido.
 * - Cerca de 4% de cancelamentos (com justificativa) e 8% de pedidos com desconto de até 15%.
 * - Os pedidos seguem o fluxo obrigatório de status, com histórico, responsáveis e datas de
 *   saída/finalização. Pedidos recentes ficam "em andamento" conforme o horário atual, e
 *   alguns pedidos dos últimos minutos garantem dados na fila de pedidos em aberto.
 *
 * É determinístico (mesma semente = mesmos pedidos), o que permite testá-lo.
 */
public class GeradorPedidosDemonstracao {

    private static final int[] PEDIDOS_POR_DIA_DA_SEMANA = {9, 10, 12, 14, 22, 26, 20}; // segunda..domingo
    private static final int[] HORAS = {11, 12, 13, 14, 17, 18, 19, 20, 21, 22, 23};
    private static final int[] PESO_DAS_HORAS = {3, 6, 4, 1, 1, 5, 12, 14, 10, 5, 2};
    private static final String[] ATENDENTES = {"atendente01", "atendente01", "gerente01"};
    private static final String GERENTE = "gerente01";
    private static final int[] MINUTOS_DOS_PEDIDOS_RECENTES = {3, 9, 16, 24, 33, 47, 58, 72};

    private final Random aleatorio;

    public GeradorPedidosDemonstracao(long semente) {
        this.aleatorio = new Random(semente);
    }

    /**
     * @param dias     quantidade de dias de histórico (terminando hoje)
     * @param agora    data/hora atual; nada é gerado depois dela
     * @param produtos produtos ativos já gravados (com id, nome, tipo e preço)
     * @param clientes clientes já gravados (com id e nome)
     */
    public List<Pedido> gerar(int dias, LocalDateTime agora, List<Produto> produtos, List<Cliente> clientes) {
        List<Pedido> pedidos = new ArrayList<>();
        if (produtos.isEmpty() || clientes.isEmpty() || dias <= 0) {
            return pedidos;
        }
        LocalDate hoje = agora.toLocalDate();
        for (int i = dias - 1; i >= 0; i--) {
            LocalDate dia = hoje.minusDays(i);
            double crescimento = 0.85 + 0.30 * (dias - 1 - i) / Math.max(1, dias - 1);
            int base = PEDIDOS_POR_DIA_DA_SEMANA[dia.getDayOfWeek().getValue() - 1];
            int quantidade = (int) Math.round(base * crescimento * (0.8 + aleatorio.nextDouble() * 0.4));
            for (int n = 0; n < quantidade; n++) {
                LocalDateTime entrada = dia.atTime(sortearHora(dia.getDayOfWeek()), aleatorio.nextInt(60));
                if (entrada.isAfter(agora)) {
                    continue;
                }
                pedidos.add(criarPedido(entrada, agora, produtos, clientes, 1.0));
            }
        }
        // Pedidos dos últimos minutos, com preparo mais lento: garantem que a fila de
        // "pedidos em aberto" (e alguns atrasados) tenha dados em qualquer horário da apresentação.
        for (int minutos : MINUTOS_DOS_PEDIDOS_RECENTES) {
            LocalDateTime entrada = agora.minusMinutes(minutos).withSecond(0).withNano(0);
            pedidos.add(criarPedido(entrada, agora, produtos, clientes, 1.8));
        }
        return pedidos;
    }

    private Pedido criarPedido(LocalDateTime entrada, LocalDateTime agora, List<Produto> produtos,
            List<Cliente> clientes, double fatorTempo) {
        Cliente cliente = clientes.get(aleatorio.nextInt(clientes.size()));
        String atendente = ATENDENTES[aleatorio.nextInt(ATENDENTES.length)];

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(aleatorio.nextDouble() < 0.65 ? TipoEntrega.ENTREGA : TipoEntrega.RETIRADA);
        pedido.setItens(sortearItens(produtos));
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(entrada);
        pedido.setRegistradoPor(atendente);
        pedido.getHistorico().add(new HistoricoStatus(null, StatusPedido.RECEBIDO, entrada, atendente, perfil(atendente)));

        if (aleatorio.nextDouble() < 0.08) {
            int[] opcoes = {5, 10, 15};
            pedido.setPercentualDesconto(new BigDecimal(opcoes[aleatorio.nextInt(opcoes.length)]).setScale(2));
            pedido.setDescontoAplicadoPor(GERENTE);
            pedido.setDataHoraDesconto(entrada.plusMinutes(2));
            pedido.setJustificativaDesconto("Cliente fidelidade");
        }
        pedido.recalcularValores();

        boolean seraCancelado = aleatorio.nextDouble() < 0.04;
        LocalDateTime momento = entrada;
        StatusPedido[] fluxo = {StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO,
            pedido.getTipoEntrega().getStatusDeSaida(), StatusPedido.FINALIZADO};
        int[][] minutosPorEtapa = {{8, 20}, {15, 30}, {4, 15}, {15, 35}};
        int etapaDoCancelamento = seraCancelado ? aleatorio.nextInt(3) : -1;

        for (int etapa = 0; etapa < fluxo.length; etapa++) {
            int minimo = minutosPorEtapa[etapa][0];
            int maximo = minutosPorEtapa[etapa][1];
            if (etapa == 3 && pedido.getTipoEntrega() == TipoEntrega.RETIRADA) {
                minimo = 1;
                maximo = 3;
            }
            momento = momento.plusMinutes(Math.round((minimo + aleatorio.nextInt(maximo - minimo + 1)) * fatorTempo));
            if (momento.isAfter(agora)) {
                break; // pedido ainda em andamento
            }
            if (etapa == etapaDoCancelamento) {
                cancelar(pedido, momento);
                break;
            }
            avancar(pedido, fluxo[etapa], momento, etapa == 3 ? GERENTE : atendente);
        }
        return pedido;
    }

    private void avancar(Pedido pedido, StatusPedido novo, LocalDateTime momento, String usuario) {
        pedido.getHistorico().add(new HistoricoStatus(pedido.getStatus(), novo, momento, usuario, perfil(usuario)));
        pedido.setStatus(novo);
        if (novo.isSaida()) {
            pedido.setDataHoraSaida(momento);
            pedido.setResponsavelSaida(usuario);
        }
        if (novo == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(momento);
            pedido.setFinalizadoPor(usuario);
        }
    }

    private void cancelar(Pedido pedido, LocalDateTime momento) {
        pedido.getHistorico().add(new HistoricoStatus(pedido.getStatus(), StatusPedido.CANCELADO, momento, GERENTE,
                perfil(GERENTE)));
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setDataHoraCancelamento(momento);
        pedido.setCanceladoPor(GERENTE);
        pedido.setJustificativaCancelamento("Cliente desistiu do pedido por telefone");
    }

    private List<ItemPedido> sortearItens(List<Produto> produtos) {
        List<ItemPedido> itens = new ArrayList<>();
        List<String> usados = new ArrayList<>();
        int quantidadeDeProdutos = 1 + aleatorio.nextInt(Math.min(3, produtos.size()));
        for (int i = 0; i < quantidadeDeProdutos; i++) {
            Produto produto = sortearProduto(produtos);
            if (usados.contains(produto.getId())) {
                continue;
            }
            usados.add(produto.getId());
            boolean esfiha = produto.getTipo() != null && produto.getTipo().equalsIgnoreCase("Esfiha");
            int quantidade = esfiha ? 4 + aleatorio.nextInt(9) : 1 + (aleatorio.nextDouble() < 0.25 ? 1 : 0);
            ItemPedido item = new ItemPedido(produto.getId(), produto.getNome(), quantidade,
                    BigDecimal.valueOf(produto.getPreco()));
            item.setCategoria(produto.getTipo());
            itens.add(item);
        }
        return itens;
    }

    /** Pizzas são mais pedidas que os demais produtos. */
    private Produto sortearProduto(List<Produto> produtos) {
        int pesoTotal = 0;
        for (Produto produto : produtos) {
            pesoTotal += peso(produto);
        }
        int sorteio = aleatorio.nextInt(pesoTotal);
        for (Produto produto : produtos) {
            sorteio -= peso(produto);
            if (sorteio < 0) {
                return produto;
            }
        }
        return produtos.get(0);
    }

    private static int peso(Produto produto) {
        String tipo = produto.getTipo() == null ? "" : produto.getTipo();
        String nome = produto.getNome() == null ? "" : produto.getNome();
        if (nome.contains("Calabresa")) {
            return 9;
        }
        if (tipo.equalsIgnoreCase("Pizza")) {
            return 5;
        }
        if (tipo.equalsIgnoreCase("Bebida")) {
            return 4;
        }
        return 2;
    }

    private int sortearHora(DayOfWeek diaDaSemana) {
        // fim de semana: almoço mais fraco, a noite concentra os pedidos
        boolean fimDeSemana = diaDaSemana == DayOfWeek.SATURDAY || diaDaSemana == DayOfWeek.SUNDAY;
        int[] pesos = new int[HORAS.length];
        int total = 0;
        for (int i = 0; i < HORAS.length; i++) {
            pesos[i] = fimDeSemana && HORAS[i] <= 14 ? Math.max(1, PESO_DAS_HORAS[i] - 2) : PESO_DAS_HORAS[i];
            total += pesos[i];
        }
        int sorteio = aleatorio.nextInt(total);
        for (int i = 0; i < HORAS.length; i++) {
            sorteio -= pesos[i];
            if (sorteio < 0) {
                return HORAS[i];
            }
        }
        return HORAS[HORAS.length - 1];
    }

    private static String perfil(String usuario) {
        return GERENTE.equals(usuario) ? "GERENTE" : "ATENDENTE";
    }
}
