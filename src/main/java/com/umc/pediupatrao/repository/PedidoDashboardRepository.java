package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Consultas de leitura usadas pelo dashboard administrativo (RF06).
 *
 * Os filtros (período, tipo de entrega, atendente e status) são aplicados no próprio
 * MongoDB, usando os índices de dataHoraEntrada e status declarados em Pedido.
 * A projeção traz apenas os campos usados nos indicadores: histórico, observação e
 * justificativas não trafegam.
 */
@Repository
public class PedidoDashboardRepository {

    /** Status que ainda não chegaram a um estado final (FINALIZADO ou CANCELADO). */
    public static final List<StatusPedido> STATUS_EM_ABERTO = List.of(StatusPedido.RECEBIDO,
            StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO);

    static final String[] CAMPOS_DO_DASHBOARD = {
        "clienteNome", "tipoEntrega", "itens", "subtotal", "percentualDesconto", "valorDesconto", "valorTotal",
        "status", "dataHoraEntrada", "dataHoraSaida", "dataHoraFinalizacao", "registradoPor"
    };

    private final MongoTemplate mongoTemplate;

    public PedidoDashboardRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Pedidos com entrada no intervalo [inicio, fimExclusivo), com os filtros opcionais. */
    public List<Pedido> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fimExclusivo, TipoEntrega tipoEntrega,
            String atendente) {
        return mongoTemplate.find(consultaPorPeriodo(inicio, fimExclusivo, tipoEntrega, atendente, null),
                Pedido.class);
    }

    /** Pedidos ainda em aberto, independentemente da data de entrada (visão em tempo real). */
    public List<Pedido> buscarEmAberto(TipoEntrega tipoEntrega) {
        return mongoTemplate.find(consultaEmAberto(tipoEntrega), Pedido.class);
    }

    /** Página de pedidos do período, do mais recente para o mais antigo. */
    public Page<Pedido> buscarPagina(LocalDateTime inicio, LocalDateTime fimExclusivo, TipoEntrega tipoEntrega,
            String atendente, StatusPedido status, int pagina, int tamanho) {
        PageRequest pageRequest = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "dataHoraEntrada"));
        Query consulta = consultaPorPeriodo(inicio, fimExclusivo, tipoEntrega, atendente, status);
        long total = mongoTemplate.count(consulta, Pedido.class);
        List<Pedido> pedidos = mongoTemplate.find(consulta.with(pageRequest), Pedido.class);
        return new PageImpl<>(pedidos, pageRequest, total);
    }

    /** Usuários que já registraram pedidos (opções do filtro "atendente"). */
    public List<String> listarAtendentes() {
        List<String> encontrados = mongoTemplate.findDistinct(new Query(), "registradoPor", Pedido.class, String.class);
        List<String> atendentes = new ArrayList<>();
        for (String atendente : encontrados) {
            if (atendente != null && !atendente.isBlank() && !atendentes.contains(atendente)) {
                atendentes.add(atendente);
            }
        }
        atendentes.sort(String.CASE_INSENSITIVE_ORDER);
        return atendentes;
    }

    // ===================== montagem das consultas (visíveis para os testes) =====================

    static Query consultaPorPeriodo(LocalDateTime inicio, LocalDateTime fimExclusivo, TipoEntrega tipoEntrega,
            String atendente, StatusPedido status) {
        Criteria criteria = Criteria.where("dataHoraEntrada").gte(inicio).lt(fimExclusivo);
        if (tipoEntrega != null) {
            criteria = criteria.and("tipoEntrega").is(tipoEntrega);
        }
        if (atendente != null) {
            criteria = criteria.and("registradoPor").is(atendente);
        }
        if (status != null) {
            criteria = criteria.and("status").is(status);
        }
        return comProjecao(new Query(criteria));
    }

    static Query consultaEmAberto(TipoEntrega tipoEntrega) {
        Criteria criteria = Criteria.where("status").in(STATUS_EM_ABERTO);
        if (tipoEntrega != null) {
            criteria = criteria.and("tipoEntrega").is(tipoEntrega);
        }
        return comProjecao(new Query(criteria));
    }

    private static Query comProjecao(Query consulta) {
        consulta.fields().include(CAMPOS_DO_DASHBOARD);
        return consulta;
    }
}
