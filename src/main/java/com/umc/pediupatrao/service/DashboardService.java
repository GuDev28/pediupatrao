package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.PaginaResposta;
import com.umc.pediupatrao.dto.dashboard.AgrupamentoSerie;
import com.umc.pediupatrao.dto.dashboard.DimensaoDistribuicao;
import com.umc.pediupatrao.dto.dashboard.DistribuicaoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.Indicador;
import com.umc.pediupatrao.dto.dashboard.ItemDistribuicao;
import com.umc.pediupatrao.dto.dashboard.OpcaoFiltro;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltroResponse;
import com.umc.pediupatrao.dto.dashboard.OrdenacaoRanking;
import com.umc.pediupatrao.dto.dashboard.PedidoResumo;
import com.umc.pediupatrao.dto.dashboard.PedidosEmAbertoResponse;
import com.umc.pediupatrao.dto.dashboard.PontoSerie;
import com.umc.pediupatrao.dto.dashboard.RankingProdutosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SerieVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidosResponse;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.repository.PedidoDashboardRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import com.umc.pediupatrao.service.dashboard.CalculadoraDashboard;
import com.umc.pediupatrao.service.dashboard.CriteriosDashboard;
import com.umc.pediupatrao.service.dashboard.LeitorParametros;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Dados do dashboard administrativo (RF01 a RF07).
 *
 * Fluxo de cada consulta: valida os filtros (CriteriosDashboard) -> busca os pedidos
 * no MongoDB já filtrados (PedidoDashboardRepository) -> calcula os indicadores
 * (CalculadoraDashboard) -> devolve um DTO com o período efetivamente usado e a
 * data/hora em que os dados foram gerados.
 *
 * Acesso somente de leitura para ADMIN e GERENTE (o responsável pelo estabelecimento).
 * A regra também está no SecurityConfig; o @PreAuthorize garante a proteção mesmo se o
 * serviço for chamado por outro controlador.
 */
@Service
public class DashboardService {

    public static final int LIMITE_RANKING_PADRAO = 10;
    public static final int LIMITE_RANKING_MAXIMO = 50;
    public static final int MINUTOS_ATRASO_PADRAO = 45;
    public static final int LIMITE_FILA_PADRAO = 20;
    public static final int TAMANHO_PAGINA_PADRAO = 10;
    public static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final PedidoDashboardRepository pedidoDashboardRepository;
    private final ProdutoRepository produtoRepository;
    private final UsuarioLogado usuarioLogado;
    private final Clock clock;

    public DashboardService(PedidoDashboardRepository pedidoDashboardRepository, ProdutoRepository produtoRepository,
            UsuarioLogado usuarioLogado, Clock clock) {
        this.pedidoDashboardRepository = pedidoDashboardRepository;
        this.produtoRepository = produtoRepository;
        this.usuarioLogado = usuarioLogado;
        this.clock = clock;
    }

    // ===================== RF05 - opções dos filtros =====================

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public OpcoesFiltroResponse opcoesDeFiltro() {
        List<OpcaoFiltro> tipos = new ArrayList<>();
        for (TipoEntrega tipo : TipoEntrega.values()) {
            tipos.add(new OpcaoFiltro(tipo.name(), tipo.getDescricao()));
        }
        List<OpcaoFiltro> status = new ArrayList<>();
        for (StatusPedido s : StatusPedido.values()) {
            status.add(new OpcaoFiltro(s.name(), s.getDescricao()));
        }
        List<OpcaoFiltro> agrupamentos = new ArrayList<>();
        for (AgrupamentoSerie a : AgrupamentoSerie.values()) {
            agrupamentos.add(new OpcaoFiltro(a.name(), a.getDescricao()));
        }
        List<OpcaoFiltro> dimensoes = new ArrayList<>();
        for (DimensaoDistribuicao d : DimensaoDistribuicao.values()) {
            dimensoes.add(new OpcaoFiltro(d.name(), d.getDescricao()));
        }
        List<OpcaoFiltro> ordenacoes = new ArrayList<>();
        for (OrdenacaoRanking o : OrdenacaoRanking.values()) {
            ordenacoes.add(new OpcaoFiltro(o.name(), o.getDescricao()));
        }
        List<String> categorias = new ArrayList<>();
        for (String categoria : categoriaPorProduto().values()) {
            if (!categorias.contains(categoria)) {
                categorias.add(categoria);
            }
        }
        categorias.sort(String.CASE_INSENSITIVE_ORDER);

        return new OpcoesFiltroResponse(CriteriosDashboard.periodoPadrao(hoje()), CriteriosDashboard.MAXIMO_DIAS,
                tipos, status, categorias, pedidoDashboardRepository.listarAtendentes(), agrupamentos, dimensoes,
                ordenacoes);
    }

    // ===================== RF01 - indicadores gerais =====================

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResumoVendasResponse resumo(FiltroDashboard filtro) {
        CriteriosDashboard criterios = CriteriosDashboard.de(filtro, hoje());
        CriteriosDashboard anterior = criterios.anterior();

        CalculadoraDashboard.Totais atual = CalculadoraDashboard.totalizar(buscar(criterios));
        CalculadoraDashboard.Totais passado = CalculadoraDashboard.totalizar(buscar(anterior));

        return new ResumoVendasResponse(
                criterios.periodo(),
                anterior.periodo(),
                indicador(atual.faturamento(), passado.faturamento()),
                indicador(BigDecimal.valueOf(atual.pedidosValidos()), BigDecimal.valueOf(passado.pedidosValidos())),
                indicador(atual.ticketMedio(), passado.ticketMedio()),
                indicador(BigDecimal.valueOf(atual.itensVendidos()), BigDecimal.valueOf(passado.itensVendidos())),
                atual.cancelados(),
                atual.taxaCancelamento(),
                atual.valorCancelado(),
                atual.comDesconto(),
                atual.valorDescontos(),
                atual.emAndamento(),
                agora());
    }

    // ===================== RF02 - análise de vendas =====================

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public SerieVendasResponse serieDeVendas(FiltroDashboard filtro, String agrupamento) {
        CriteriosDashboard criterios = CriteriosDashboard.de(filtro, hoje());
        AgrupamentoSerie escolhido = LeitorParametros.opcao(agrupamento, AgrupamentoSerie.class, "agrupamento");
        if (escolhido == null) {
            escolhido = AgrupamentoSerie.automatico(criterios.dias());
        }
        List<PontoSerie> pontos = CalculadoraDashboard.serie(buscar(criterios), criterios.dataInicio(),
                criterios.dataFim(), escolhido);

        BigDecimal total = BigDecimal.ZERO;
        long pedidos = 0;
        for (PontoSerie ponto : pontos) {
            total = total.add(ponto.faturamento());
            pedidos += ponto.pedidos();
        }
        return new SerieVendasResponse(criterios.periodo(), escolhido, pontos, CalculadoraDashboard.dinheiro(total),
                pedidos, agora());
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public DistribuicaoVendasResponse distribuicao(FiltroDashboard filtro, String dimensao) {
        CriteriosDashboard criterios = CriteriosDashboard.de(filtro, hoje());
        DimensaoDistribuicao escolhida = LeitorParametros.opcao(dimensao, DimensaoDistribuicao.class, "dimensao");
        if (escolhida == null) {
            escolhida = DimensaoDistribuicao.HORA;
        }
        Map<String, String> categorias = escolhida == DimensaoDistribuicao.CATEGORIA
                ? categoriaPorProduto() : new HashMap<>();
        List<ItemDistribuicao> itens = CalculadoraDashboard.distribuicao(buscar(criterios), escolhida, categorias);

        BigDecimal total = BigDecimal.ZERO;
        for (ItemDistribuicao item : itens) {
            total = total.add(item.faturamento());
        }
        return new DistribuicaoVendasResponse(criterios.periodo(), escolhida, itens,
                CalculadoraDashboard.dinheiro(total), agora());
    }

    // ===================== RF04 - produtos mais vendidos =====================

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public RankingProdutosResponse produtosMaisVendidos(FiltroDashboard filtro, String categoria, String ordenarPor,
            Integer limite) {
        CriteriosDashboard criterios = CriteriosDashboard.de(filtro, hoje());
        OrdenacaoRanking ordenacao = LeitorParametros.opcao(ordenarPor, OrdenacaoRanking.class, "ordenarPor");
        if (ordenacao == null) {
            ordenacao = OrdenacaoRanking.QUANTIDADE;
        }
        int quantidade = LeitorParametros.inteiro(limite, LIMITE_RANKING_PADRAO, 1, LIMITE_RANKING_MAXIMO, "limite");
        String categoriaFiltro = LeitorParametros.limpar(categoria);

        CalculadoraDashboard.Ranking ranking = CalculadoraDashboard.ranking(buscar(criterios), categoriaPorProduto(),
                categoriaFiltro, ordenacao, quantidade);
        return new RankingProdutosResponse(criterios.periodo(), ordenacao, categoriaFiltro, quantidade,
                ranking.totalProdutos(), ranking.quantidadeTotal(), ranking.faturamentoTotal(), ranking.itens(),
                agora());
    }

    // ===================== RF03 - situação dos pedidos =====================

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public SituacaoPedidosResponse situacaoDosPedidos(FiltroDashboard filtro) {
        CriteriosDashboard criterios = CriteriosDashboard.de(filtro, hoje());
        CalculadoraDashboard.Situacao situacao = CalculadoraDashboard.situacao(buscar(criterios));
        return new SituacaoPedidosResponse(criterios.periodo(), situacao.total(), situacao.porStatus(),
                situacao.emAndamento(), situacao.finalizados(), situacao.cancelados(),
                situacao.tempoMedioAteSaidaMinutos(), situacao.tempoMedioAteFinalizacaoMinutos(), agora());
    }

    /**
     * Pedidos em aberto agora (tempo real). Não usa o período: um pedido de ontem ainda
     * aberto precisa aparecer. Aceita apenas o filtro de tipo de entrega.
     */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public PedidosEmAbertoResponse pedidosEmAberto(String tipoEntrega, Integer limiteMinutos, Integer limite) {
        TipoEntrega tipo = LeitorParametros.opcao(tipoEntrega, TipoEntrega.class, "tipoEntrega");
        int minutos = LeitorParametros.inteiro(limiteMinutos, MINUTOS_ATRASO_PADRAO, 5, 240, "limiteMinutos");
        int quantidade = LeitorParametros.inteiro(limite, LIMITE_FILA_PADRAO, 1, 100, "limite");

        LocalDateTime agora = agora();
        CalculadoraDashboard.FilaEmAberto fila = CalculadoraDashboard.pedidosEmAberto(
                pedidoDashboardRepository.buscarEmAberto(tipo), agora, minutos, quantidade, mascararDadosPessoais());
        return new PedidosEmAbertoResponse(minutos, fila.total(), fila.atrasados(), fila.pedidos(), agora);
    }

    /** Lista paginada dos pedidos do período, com filtro opcional de status. */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public PaginaResposta<PedidoResumo> listarPedidos(FiltroDashboard filtro, String status, Integer pagina,
            Integer tamanho) {
        CriteriosDashboard criterios = CriteriosDashboard.de(filtro, hoje());
        StatusPedido statusFiltro = LeitorParametros.opcao(status, StatusPedido.class, "status");
        int numeroPagina = LeitorParametros.inteiro(pagina, 0, 0, 100_000, "pagina");
        int tamanhoPagina = LeitorParametros.inteiro(tamanho, TAMANHO_PAGINA_PADRAO, 1, TAMANHO_PAGINA_MAXIMO,
                "tamanho");

        Page<Pedido> resultado = pedidoDashboardRepository.buscarPagina(criterios.inicio(), criterios.fimExclusivo(),
                criterios.tipoEntrega(), criterios.atendente(), statusFiltro, numeroPagina, tamanhoPagina);
        boolean mascarar = mascararDadosPessoais();
        List<PedidoResumo> linhas = new ArrayList<>();
        for (Pedido pedido : resultado.getContent()) {
            linhas.add(CalculadoraDashboard.resumir(pedido, mascarar));
        }
        return new PaginaResposta<>(linhas, resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    // ===================== apoio =====================

    private List<Pedido> buscar(CriteriosDashboard criterios) {
        return pedidoDashboardRepository.buscarPorPeriodo(criterios.inicio(), criterios.fimExclusivo(),
                criterios.tipoEntrega(), criterios.atendente());
    }

    /** Categoria atual de cada produto do cadastro (id -> tipo), para itens antigos sem categoria. */
    private Map<String, String> categoriaPorProduto() {
        Map<String, String> categorias = new HashMap<>();
        for (Produto produto : produtoRepository.findAll()) {
            String tipo = LeitorParametros.limpar(produto.getTipo());
            if (produto.getId() != null && tipo != null) {
                categorias.put(produto.getId(), tipo);
            }
        }
        return categorias;
    }

    private static Indicador indicador(BigDecimal atual, BigDecimal anterior) {
        return new Indicador(atual, anterior, CalculadoraDashboard.variacaoPercentual(atual, anterior));
    }

    /** Minimização de dados pessoais: o ADMIN vê o nome do cliente mascarado (mesmo critério da auditoria). */
    private boolean mascararDadosPessoais() {
        return usuarioLogado.possuiPerfil(Perfil.ADMIN);
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock).withNano(0);
    }
}
