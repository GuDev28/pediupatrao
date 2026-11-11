package com.umc.pediupatrao.service.dashboard;

import com.umc.pediupatrao.dto.dashboard.AgrupamentoSerie;
import com.umc.pediupatrao.dto.dashboard.DimensaoDistribuicao;
import com.umc.pediupatrao.dto.dashboard.ItemDistribuicao;
import com.umc.pediupatrao.dto.dashboard.OrdenacaoRanking;
import com.umc.pediupatrao.dto.dashboard.PedidoEmAberto;
import com.umc.pediupatrao.dto.dashboard.PedidoResumo;
import com.umc.pediupatrao.dto.dashboard.PontoSerie;
import com.umc.pediupatrao.dto.dashboard.ProdutoRanking;
import com.umc.pediupatrao.dto.dashboard.QuantidadePorStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cálculos dos indicadores do dashboard (RF01 a RF04 e RF07).
 *
 * Classe sem dependências de Spring ou de banco: recebe os pedidos já filtrados pelo
 * repositório e devolve os números prontos para a API. Isso permite testar todas as
 * regras com listas montadas em memória.
 *
 * Regras de negócio:
 * - Venda válida: todo pedido que NÃO está CANCELADO (inclusive os ainda em andamento,
 *   pois o cancelamento só é possível antes da saída e é contabilizado à parte).
 * - Valores monetários com 2 casas e percentuais/médias com 1 casa (HALF_UP).
 * - Faturamento por item/categoria/produto é LÍQUIDO: subtotal do item menos o mesmo
 *   percentual de desconto aplicado ao pedido inteiro, com os centavos de arredondamento
 *   ajustados para que a soma dos itens feche com o total do pedido.
 * - Dados incompletos (pedidos antigos sem data, sem status ou sem valores) não
 *   quebram os cálculos: valores nulos contam como zero e datas nulas são ignoradas.
 */
public final class CalculadoraDashboard {

    public static final String SEM_CATEGORIA = "Sem categoria";
    public static final String NAO_INFORMADO = "Não informado";

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final String[] MESES = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out",
        "nov", "dez"};
    private static final String[] DIAS_SEMANA = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};

    private CalculadoraDashboard() {
    }

    // =====================================================================
    // RF01 - Indicadores gerais
    // =====================================================================

    /** Totais de um conjunto de pedidos, usados no resumo do período atual e do anterior. */
    public record Totais(long registrados, long pedidosValidos, BigDecimal faturamento, long itensVendidos,
            long cancelados, BigDecimal valorCancelado, long comDesconto, BigDecimal valorDescontos,
            long emAndamento) {

        public BigDecimal ticketMedio() {
            return dividir(faturamento, pedidosValidos);
        }

        public BigDecimal taxaCancelamento() {
            return percentual(cancelados, registrados);
        }
    }

    public static Totais totalizar(List<Pedido> pedidos) {
        long registrados = 0;
        long validos = 0;
        long itens = 0;
        long cancelados = 0;
        long comDesconto = 0;
        long emAndamento = 0;
        BigDecimal faturamento = BigDecimal.ZERO;
        BigDecimal valorCancelado = BigDecimal.ZERO;
        BigDecimal descontos = BigDecimal.ZERO;

        for (Pedido pedido : seguro(pedidos)) {
            if (pedido == null) {
                continue;
            }
            registrados++;
            if (!isVendaValida(pedido)) {
                cancelados++;
                valorCancelado = valorCancelado.add(valor(pedido.getValorTotal()));
                continue;
            }
            validos++;
            faturamento = faturamento.add(valor(pedido.getValorTotal()));
            itens += pedido.getQuantidadeTotalItens();
            if (pedido.isPossuiDesconto()) {
                comDesconto++;
                descontos = descontos.add(valor(pedido.getValorDesconto()));
            }
            if (pedido.getStatus() != null && !pedido.getStatus().isFinal()) {
                emAndamento++;
            }
        }
        return new Totais(registrados, validos, dinheiro(faturamento), itens, cancelados, dinheiro(valorCancelado),
                comDesconto, dinheiro(descontos), emAndamento);
    }

    /** Pedido que conta como venda: tudo, exceto os cancelados. */
    public static boolean isVendaValida(Pedido pedido) {
        return pedido != null && pedido.getStatus() != StatusPedido.CANCELADO;
    }

    /**
     * Variação percentual entre o período atual e o anterior.
     * Retorna null quando o anterior é zero (não existe base para comparar).
     */
    public static BigDecimal variacaoPercentual(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) {
            return null;
        }
        return valor(atual).subtract(anterior).multiply(CEM).divide(anterior.abs(), 1, RoundingMode.HALF_UP);
    }

    // =====================================================================
    // RF02 - Série temporal
    // =====================================================================

    /**
     * Soma as vendas válidas por dia, semana (segunda a domingo) ou mês, incluindo os
     * intervalos sem vendas com zero para que o gráfico não "pule" datas. A primeira e a
     * última semana/mês são recortadas para ficar dentro do período consultado.
     */
    public static List<PontoSerie> serie(List<Pedido> pedidos, LocalDate inicio, LocalDate fim,
            AgrupamentoSerie agrupamento) {
        Map<LocalDate, Acumulador> baldes = new LinkedHashMap<>();
        LocalDate cursor = inicioDoBalde(inicio, agrupamento);
        while (!cursor.isAfter(fim)) {
            baldes.put(cursor, new Acumulador());
            cursor = proximoBalde(cursor, agrupamento);
        }

        for (Pedido pedido : seguro(pedidos)) {
            if (!isVendaValida(pedido) || pedido.getDataHoraEntrada() == null) {
                continue;
            }
            LocalDate dia = pedido.getDataHoraEntrada().toLocalDate();
            if (dia.isBefore(inicio) || dia.isAfter(fim)) {
                continue;
            }
            baldes.get(inicioDoBalde(dia, agrupamento)).somarPedido(pedido);
        }

        List<PontoSerie> pontos = new ArrayList<>();
        for (Map.Entry<LocalDate, Acumulador> balde : baldes.entrySet()) {
            LocalDate inicioPonto = balde.getKey().isBefore(inicio) ? inicio : balde.getKey();
            LocalDate fimBalde = proximoBalde(balde.getKey(), agrupamento).minusDays(1);
            LocalDate fimPonto = fimBalde.isAfter(fim) ? fim : fimBalde;
            Acumulador acumulado = balde.getValue();
            pontos.add(new PontoSerie(inicioPonto, fimPonto, rotulo(balde.getKey(), inicioPonto, agrupamento),
                    dinheiro(acumulado.faturamento), acumulado.pedidos,
                    dividir(acumulado.faturamento, acumulado.pedidos)));
        }
        return pontos;
    }

    private static LocalDate inicioDoBalde(LocalDate data, AgrupamentoSerie agrupamento) {
        switch (agrupamento) {
            case SEMANA:
                return data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES:
                return data.withDayOfMonth(1);
            default:
                return data;
        }
    }

    private static LocalDate proximoBalde(LocalDate inicioBalde, AgrupamentoSerie agrupamento) {
        switch (agrupamento) {
            case SEMANA:
                return inicioBalde.plusWeeks(1);
            case MES:
                return inicioBalde.plusMonths(1);
            default:
                return inicioBalde.plusDays(1);
        }
    }

    private static String rotulo(LocalDate inicioBalde, LocalDate inicioPonto, AgrupamentoSerie agrupamento) {
        switch (agrupamento) {
            case SEMANA:
                return "Sem. " + diaMes(inicioPonto);
            case MES:
                return MESES[inicioBalde.getMonthValue() - 1] + "/" + String.format("%02d", inicioBalde.getYear() % 100);
            default:
                return diaMes(inicioPonto);
        }
    }

    private static String diaMes(LocalDate data) {
        return String.format("%02d/%02d", data.getDayOfMonth(), data.getMonthValue());
    }

    // =====================================================================
    // RF02 - Distribuição
    // =====================================================================

    /**
     * Distribui as vendas válidas pela dimensão escolhida. Hora (0-23), dia da semana
     * (segunda a domingo) e tipo de entrega trazem todas as opções, mesmo com zero.
     * Categoria traz as categorias encontradas, da maior para a menor em faturamento.
     *
     * @param categoriaPorProduto categoria atual de cada produto (id -> tipo), usada
     *                            para itens antigos gravados sem categoria
     */
    public static List<ItemDistribuicao> distribuicao(List<Pedido> pedidos, DimensaoDistribuicao dimensao,
            Map<String, String> categoriaPorProduto) {
        Map<String, Acumulador> grupos = new LinkedHashMap<>();
        Map<String, String> rotulos = new LinkedHashMap<>();
        prepararGrupos(dimensao, grupos, rotulos);

        for (Pedido pedido : seguro(pedidos)) {
            if (!isVendaValida(pedido)) {
                continue;
            }
            if (dimensao == DimensaoDistribuicao.CATEGORIA) {
                somarPorCategoria(pedido, categoriaPorProduto, grupos, rotulos);
                continue;
            }
            String chave = chaveDoPedido(pedido, dimensao);
            if (chave == null) {
                continue;
            }
            if (!grupos.containsKey(chave)) {
                grupos.put(chave, new Acumulador());
                rotulos.put(chave, NAO_INFORMADO);
            }
            grupos.get(chave).somarPedido(pedido);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Acumulador acumulador : grupos.values()) {
            total = total.add(acumulador.faturamento);
        }

        List<ItemDistribuicao> itens = new ArrayList<>();
        for (Map.Entry<String, Acumulador> grupo : grupos.entrySet()) {
            Acumulador a = grupo.getValue();
            itens.add(new ItemDistribuicao(grupo.getKey(), rotulos.get(grupo.getKey()), a.pedidos, a.itens,
                    dinheiro(a.faturamento), percentual(a.faturamento, total)));
        }
        if (dimensao == DimensaoDistribuicao.CATEGORIA) {
            itens.sort(Comparator.comparing(ItemDistribuicao::faturamento).reversed()
                    .thenComparing(ItemDistribuicao::rotulo));
        }
        return itens;
    }

    private static void prepararGrupos(DimensaoDistribuicao dimensao, Map<String, Acumulador> grupos,
            Map<String, String> rotulos) {
        switch (dimensao) {
            case HORA:
                for (int hora = 0; hora < 24; hora++) {
                    grupos.put(String.valueOf(hora), new Acumulador());
                    rotulos.put(String.valueOf(hora), String.format("%02dh", hora));
                }
                break;
            case DIA_SEMANA:
                for (int dia = 1; dia <= 7; dia++) {
                    grupos.put(String.valueOf(dia), new Acumulador());
                    rotulos.put(String.valueOf(dia), DIAS_SEMANA[dia - 1]);
                }
                break;
            case TIPO_ENTREGA:
                for (TipoEntrega tipo : TipoEntrega.values()) {
                    grupos.put(tipo.name(), new Acumulador());
                    rotulos.put(tipo.name(), tipo.getDescricao());
                }
                break;
            default:
                break;
        }
    }

    private static String chaveDoPedido(Pedido pedido, DimensaoDistribuicao dimensao) {
        switch (dimensao) {
            case HORA:
                return pedido.getDataHoraEntrada() == null ? null : String.valueOf(pedido.getDataHoraEntrada().getHour());
            case DIA_SEMANA:
                return pedido.getDataHoraEntrada() == null ? null
                        : String.valueOf(pedido.getDataHoraEntrada().getDayOfWeek().getValue());
            case TIPO_ENTREGA:
                return pedido.getTipoEntrega() == null ? "NAO_INFORMADO" : pedido.getTipoEntrega().name();
            default:
                return null;
        }
    }

    private static void somarPorCategoria(Pedido pedido, Map<String, String> categoriaPorProduto,
            Map<String, Acumulador> grupos, Map<String, String> rotulos) {
        Set<String> categoriasDoPedido = new HashSet<>();
        List<ItemPedido> itens = seguro(pedido.getItens());
        List<BigDecimal> liquidos = valoresLiquidosDosItens(pedido);
        for (int i = 0; i < itens.size(); i++) {
            ItemPedido item = itens.get(i);
            if (item == null) {
                continue;
            }
            String categoria = categoriaDoItem(item, categoriaPorProduto);
            if (!grupos.containsKey(categoria)) {
                grupos.put(categoria, new Acumulador());
                rotulos.put(categoria, categoria);
            }
            Acumulador acumulador = grupos.get(categoria);
            acumulador.itens += item.getQuantidade();
            acumulador.faturamento = acumulador.faturamento.add(liquidos.get(i));
            if (categoriasDoPedido.add(categoria)) {
                acumulador.pedidos++;
            }
        }
    }

    // =====================================================================
    // RF04 - Produtos mais vendidos
    // =====================================================================

    /** Resultado do ranking: produtos na ordem pedida (já limitados) e totais de referência. */
    public record Ranking(List<ProdutoRanking> itens, int totalProdutos, long quantidadeTotal,
            BigDecimal faturamentoTotal) {
    }

    /**
     * Agrupa os itens vendidos (pedidos válidos) por produto. Empates são decididos pelo
     * outro critério e, por fim, pelo nome do produto, para que a ordem seja sempre a mesma.
     *
     * @param categoria se informada, considera só os itens dessa categoria (sem diferenciar maiúsculas)
     */
    public static Ranking ranking(List<Pedido> pedidos, Map<String, String> categoriaPorProduto, String categoria,
            OrdenacaoRanking ordenacao, int limite) {
        Map<String, AcumuladorProduto> produtos = new LinkedHashMap<>();
        long quantidadeTotal = 0;
        BigDecimal faturamentoTotal = BigDecimal.ZERO;

        for (Pedido pedido : seguro(pedidos)) {
            if (!isVendaValida(pedido)) {
                continue;
            }
            Set<String> produtosDoPedido = new HashSet<>();
            List<ItemPedido> itens = seguro(pedido.getItens());
            List<BigDecimal> liquidos = valoresLiquidosDosItens(pedido);
            for (int i = 0; i < itens.size(); i++) {
                ItemPedido item = itens.get(i);
                if (item == null) {
                    continue;
                }
                String categoriaItem = categoriaDoItem(item, categoriaPorProduto);
                if (categoria != null && !categoria.equalsIgnoreCase(categoriaItem)) {
                    continue;
                }
                String chave = item.getProdutoId() != null ? item.getProdutoId() : "nome:" + item.getNomeProduto();
                AcumuladorProduto produto = produtos.get(chave);
                if (produto == null) {
                    produto = new AcumuladorProduto(item.getProdutoId(), item.getNomeProduto(), categoriaItem);
                    produtos.put(chave, produto);
                }
                BigDecimal liquido = liquidos.get(i);
                produto.quantidade += item.getQuantidade();
                produto.faturamento = produto.faturamento.add(liquido);
                if (produtosDoPedido.add(chave)) {
                    produto.pedidos++;
                }
                quantidadeTotal += item.getQuantidade();
                faturamentoTotal = faturamentoTotal.add(liquido);
            }
        }

        List<AcumuladorProduto> ordenados = new ArrayList<>(produtos.values());
        Comparator<AcumuladorProduto> porQuantidade = Comparator.comparingLong(p -> p.quantidade);
        Comparator<AcumuladorProduto> porFaturamento = Comparator.comparing(p -> p.faturamento);
        Comparator<AcumuladorProduto> criterio = ordenacao == OrdenacaoRanking.FATURAMENTO
                ? porFaturamento.reversed().thenComparing(porQuantidade.reversed())
                : porQuantidade.reversed().thenComparing(porFaturamento.reversed());
        ordenados.sort(criterio.thenComparing(p -> p.nome == null ? "" : p.nome));

        List<ProdutoRanking> itens = new ArrayList<>();
        for (int i = 0; i < ordenados.size() && i < limite; i++) {
            AcumuladorProduto p = ordenados.get(i);
            itens.add(new ProdutoRanking(i + 1, p.produtoId, p.nome, p.categoria, p.quantidade,
                    dinheiro(p.faturamento), p.pedidos, percentual(p.quantidade, quantidadeTotal),
                    percentual(p.faturamento, faturamentoTotal)));
        }
        return new Ranking(itens, ordenados.size(), quantidadeTotal, dinheiro(faturamentoTotal));
    }

    // =====================================================================
    // RF03 - Situação dos pedidos
    // =====================================================================

    /** Contagens por status e tempos médios dos pedidos registrados no período. */
    public record Situacao(long total, List<QuantidadePorStatus> porStatus, long emAndamento, long finalizados,
            long cancelados, BigDecimal tempoMedioAteSaidaMinutos, BigDecimal tempoMedioAteFinalizacaoMinutos) {
    }

    public static Situacao situacao(List<Pedido> pedidos) {
        Map<StatusPedido, Acumulador> porStatus = new LinkedHashMap<>();
        for (StatusPedido status : StatusPedido.values()) {
            porStatus.put(status, new Acumulador());
        }
        Acumulador semStatus = new Acumulador();
        long total = 0;
        long emAndamento = 0;
        long minutosAteSaida = 0;
        long comSaida = 0;
        long minutosAteFinalizacao = 0;
        long comFinalizacao = 0;

        for (Pedido pedido : seguro(pedidos)) {
            if (pedido == null) {
                continue;
            }
            total++;
            StatusPedido status = pedido.getStatus();
            Acumulador acumulador = status == null ? semStatus : porStatus.get(status);
            acumulador.pedidos++;
            acumulador.faturamento = acumulador.faturamento.add(valor(pedido.getValorTotal()));
            if (status != null && !status.isFinal()) {
                emAndamento++;
            }
            Long ateSaida = minutosEntre(pedido.getDataHoraEntrada(), pedido.getDataHoraSaida());
            if (ateSaida != null) {
                minutosAteSaida += ateSaida;
                comSaida++;
            }
            Long ateFinalizacao = minutosEntre(pedido.getDataHoraEntrada(), pedido.getDataHoraFinalizacao());
            if (ateFinalizacao != null) {
                minutosAteFinalizacao += ateFinalizacao;
                comFinalizacao++;
            }
        }

        List<QuantidadePorStatus> lista = new ArrayList<>();
        for (Map.Entry<StatusPedido, Acumulador> item : porStatus.entrySet()) {
            Acumulador a = item.getValue();
            lista.add(new QuantidadePorStatus(item.getKey(), item.getKey().getDescricao(), a.pedidos,
                    percentual(a.pedidos, total), dinheiro(a.faturamento)));
        }
        if (semStatus.pedidos > 0) {
            lista.add(new QuantidadePorStatus(null, NAO_INFORMADO, semStatus.pedidos,
                    percentual(semStatus.pedidos, total), dinheiro(semStatus.faturamento)));
        }
        return new Situacao(total, lista, emAndamento, porStatus.get(StatusPedido.FINALIZADO).pedidos,
                porStatus.get(StatusPedido.CANCELADO).pedidos, media(minutosAteSaida, comSaida),
                media(minutosAteFinalizacao, comFinalizacao));
    }

    /** Fila de pedidos em aberto: os que esperam há mais tempo primeiro. */
    public record FilaEmAberto(long total, long atrasados, List<PedidoEmAberto> pedidos) {
    }

    public static FilaEmAberto pedidosEmAberto(List<Pedido> pedidos, LocalDateTime agora, int limiteMinutos,
            int limite, boolean mascararNomes) {
        List<PedidoEmAberto> abertos = new ArrayList<>();
        long atrasados = 0;
        for (Pedido pedido : seguro(pedidos)) {
            if (pedido == null || pedido.getStatus() == null || pedido.getStatus().isFinal()) {
                continue;
            }
            Long minutos = minutosEntre(pedido.getDataHoraEntrada(), agora);
            boolean atrasado = minutos != null && minutos > limiteMinutos;
            if (atrasado) {
                atrasados++;
            }
            abertos.add(new PedidoEmAberto(pedido.getId(), pedido.getCodigo(),
                    nomeCliente(pedido.getClienteNome(), mascararNomes), pedido.getTipoEntrega(), pedido.getStatus(),
                    pedido.getStatus().getDescricao(), dinheiro(valor(pedido.getValorTotal())),
                    pedido.getDataHoraEntrada(), minutos, atrasado));
        }
        // Mais antigos primeiro; pedidos sem data de entrada vão para o fim.
        abertos.sort(Comparator.comparing(PedidoEmAberto::minutosDesdeEntrada,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<PedidoEmAberto> primeiros = new ArrayList<>();
        for (int i = 0; i < abertos.size() && i < limite; i++) {
            primeiros.add(abertos.get(i));
        }
        return new FilaEmAberto(abertos.size(), atrasados, primeiros);
    }

    /** Linha da lista de pedidos do dashboard. */
    public static PedidoResumo resumir(Pedido pedido, boolean mascararNomes) {
        LocalDateTime fimAtendimento = pedido.getDataHoraFinalizacao() != null
                ? pedido.getDataHoraFinalizacao() : pedido.getDataHoraSaida();
        StatusPedido status = pedido.getStatus();
        return new PedidoResumo(pedido.getId(), pedido.getCodigo(), nomeCliente(pedido.getClienteNome(), mascararNomes),
                pedido.getTipoEntrega(), status, status == null ? NAO_INFORMADO : status.getDescricao(),
                pedido.getQuantidadeTotalItens(), dinheiro(valor(pedido.getValorTotal())),
                pedido.getPercentualDesconto(), pedido.getDataHoraEntrada(), pedido.getDataHoraSaida(),
                pedido.getRegistradoPor(), minutosEntre(pedido.getDataHoraEntrada(), fimAtendimento));
    }

    // =====================================================================
    // Tratamento de dados (RF07)
    // =====================================================================

    /**
     * Categoria de um item: a gravada no pedido; para itens antigos, a do cadastro atual do
     * produto; se nenhuma existir, "Sem categoria".
     */
    public static String categoriaDoItem(ItemPedido item, Map<String, String> categoriaPorProduto) {
        String categoria = LeitorParametros.limpar(item.getCategoria());
        if (categoria == null && item.getProdutoId() != null && categoriaPorProduto != null) {
            categoria = LeitorParametros.limpar(categoriaPorProduto.get(item.getProdutoId()));
        }
        return categoria == null ? SEM_CATEGORIA : categoria;
    }

    /**
     * Valor líquido de cada item do pedido (mesma ordem da lista de itens). O desconto do
     * pedido é rateado pelo percentual e a diferença de arredondamento (centavos) fica no
     * último item, para que a soma dos itens seja exatamente o valor total do pedido.
     */
    public static List<BigDecimal> valoresLiquidosDosItens(Pedido pedido) {
        List<BigDecimal> valores = new ArrayList<>();
        BigDecimal soma = BigDecimal.ZERO;
        int ultimo = -1;
        List<ItemPedido> itens = seguro(pedido.getItens());
        for (int i = 0; i < itens.size(); i++) {
            ItemPedido item = itens.get(i);
            BigDecimal liquido = item == null ? BigDecimal.ZERO : valorLiquidoDoItem(item, pedido);
            valores.add(liquido);
            soma = soma.add(liquido);
            if (item != null) {
                ultimo = i;
            }
        }
        BigDecimal total = pedido.getValorTotal();
        if (ultimo >= 0 && total != null && total.signum() > 0) {
            BigDecimal diferenca = total.subtract(soma);
            // Só corrige centavos de arredondamento; diferenças maiores indicam dado inconsistente.
            if (diferenca.abs().compareTo(new BigDecimal("0.05")) <= 0) {
                valores.set(ultimo, valores.get(ultimo).add(diferenca));
            }
        }
        return valores;
    }

    /** Subtotal do item com o mesmo percentual de desconto aplicado ao pedido. */
    public static BigDecimal valorLiquidoDoItem(ItemPedido item, Pedido pedido) {
        BigDecimal subtotal = item.getSubtotal() != null ? item.getSubtotal()
                : valor(item.getPrecoUnitario()).multiply(BigDecimal.valueOf(item.getQuantidade()));
        BigDecimal desconto = valor(pedido.getPercentualDesconto());
        if (desconto.signum() == 0) {
            return dinheiro(subtotal);
        }
        return subtotal.multiply(CEM.subtract(desconto)).divide(CEM, 2, RoundingMode.HALF_UP);
    }

    /**
     * Dado pessoal minimizado para o ADMIN (mesmo critério da auditoria): mantém só a
     * inicial de cada palavra, ex.: "Maria Souza" -> "M*** S***".
     */
    public static String nomeCliente(String nome, boolean mascarar) {
        if (!mascarar || nome == null || nome.isBlank()) {
            return nome;
        }
        StringBuilder sb = new StringBuilder();
        for (String parte : nome.trim().split("\\s+")) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(parte.charAt(0)).append("***");
        }
        return sb.toString();
    }

    public static BigDecimal valor(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    public static BigDecimal dinheiro(BigDecimal valor) {
        return valor(valor).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal dividir(BigDecimal valor, long quantidade) {
        if (quantidade <= 0) {
            return dinheiro(BigDecimal.ZERO);
        }
        return valor(valor).divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentual(long parte, long total) {
        return percentual(BigDecimal.valueOf(parte), BigDecimal.valueOf(total));
    }

    public static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return valor(parte).multiply(CEM).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal media(long soma, long quantidade) {
        if (quantidade == 0) {
            return null;
        }
        return BigDecimal.valueOf(soma).divide(BigDecimal.valueOf(quantidade), 1, RoundingMode.HALF_UP);
    }

    /** Minutos entre dois instantes; null se faltar algum ou se o intervalo for negativo (dado inconsistente). */
    public static Long minutosEntre(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            return null;
        }
        return Duration.between(inicio, fim).toMinutes();
    }

    private static <T> List<T> seguro(List<T> lista) {
        return lista == null ? new ArrayList<>() : lista;
    }

    /** Acumula pedidos, itens e faturamento de um grupo. */
    private static final class Acumulador {
        private long pedidos;
        private long itens;
        private BigDecimal faturamento = BigDecimal.ZERO;

        void somarPedido(Pedido pedido) {
            pedidos++;
            itens += pedido.getQuantidadeTotalItens();
            faturamento = faturamento.add(valor(pedido.getValorTotal()));
        }
    }

    private static final class AcumuladorProduto {
        private final String produtoId;
        private final String nome;
        private final String categoria;
        private long quantidade;
        private long pedidos;
        private BigDecimal faturamento = BigDecimal.ZERO;

        AcumuladorProduto(String produtoId, String nome, String categoria) {
            this.produtoId = produtoId;
            this.nome = nome;
            this.categoria = categoria;
        }
    }
}
