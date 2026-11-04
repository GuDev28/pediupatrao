package com.umc.pediupatrao.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Verifica, sem um MongoDB rodando, as consultas enviadas ao banco pelo dashboard:
 * filtros aplicados no servidor de banco, intervalo de datas e projeção de campos.
 */
class PedidoDashboardRepositoryTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2026, 11, 1, 0, 0);

    @Test
    void consultaPorPeriodoUsaIntervaloSemiabertoNaDataDeEntrada() {
        Document filtro = PedidoDashboardRepository.consultaPorPeriodo(INICIO, FIM, null, null, null)
                .getQueryObject();

        Document entrada = (Document) filtro.get("dataHoraEntrada");
        assertThat(entrada.get("$gte")).isEqualTo(INICIO);
        assertThat(entrada.get("$lt")).isEqualTo(FIM);
        assertThat(filtro.keySet()).containsExactly("dataHoraEntrada");
    }

    @Test
    void filtrosOpcionaisSoEntramQuandoInformados() {
        Document filtro = PedidoDashboardRepository.consultaPorPeriodo(INICIO, FIM, TipoEntrega.RETIRADA,
                "atendente01", StatusPedido.CANCELADO).getQueryObject();

        assertThat(filtro.get("tipoEntrega")).isEqualTo(TipoEntrega.RETIRADA);
        assertThat(filtro.get("registradoPor")).isEqualTo("atendente01");
        assertThat(filtro.get("status")).isEqualTo(StatusPedido.CANCELADO);
    }

    @Test
    void projecaoTrazSoOsCamposUsadosPeloDashboard() {
        Document campos = PedidoDashboardRepository.consultaPorPeriodo(INICIO, FIM, null, null, null)
                .getFieldsObject();

        assertThat(campos.keySet()).containsExactlyInAnyOrderElementsOf(
                Arrays.asList(PedidoDashboardRepository.CAMPOS_DO_DASHBOARD));
        assertThat(campos).doesNotContainKeys("historico", "observacao", "justificativaCancelamento");
    }

    @Test
    void pedidosEmAbertoSaoOsQueNaoChegaramAUmEstadoFinal() {
        Document filtro = PedidoDashboardRepository.consultaEmAberto(TipoEntrega.ENTREGA).getQueryObject();

        @SuppressWarnings("unchecked")
        List<StatusPedido> status = (List<StatusPedido>) ((Document) filtro.get("status")).get("$in");
        assertThat(status).doesNotContain(StatusPedido.FINALIZADO, StatusPedido.CANCELADO).hasSize(5);
        assertThat(filtro.get("tipoEntrega")).isEqualTo(TipoEntrega.ENTREGA);
        assertThat(filtro).doesNotContainKey("dataHoraEntrada");
    }

    @Test
    void paginaOrdenaPelaEntradaMaisRecenteEContaOTotal() {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.count(any(Query.class), eq(Pedido.class))).thenReturn(42L);
        when(template.find(any(Query.class), eq(Pedido.class))).thenReturn(new ArrayList<>());
        PedidoDashboardRepository repository = new PedidoDashboardRepository(template);

        Page<Pedido> pagina = repository.buscarPagina(INICIO, FIM, null, null, null, 2, 10);

        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(template).find(consulta.capture(), eq(Pedido.class));
        assertThat(consulta.getValue().getSkip()).isEqualTo(20);
        assertThat(consulta.getValue().getLimit()).isEqualTo(10);
        assertThat(consulta.getValue().getSortObject().get("dataHoraEntrada")).isEqualTo(-1);
        assertThat(pagina.getTotalElements()).isEqualTo(42);
        assertThat(pagina.getTotalPages()).isEqualTo(5);
    }

    @Test
    void atendentesSemRepeticaoNemValoresVazios() {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.findDistinct(any(Query.class), eq("registradoPor"), eq(Pedido.class), eq(String.class)))
                .thenReturn(Arrays.asList("gerente01", null, "atendente01", " ", "gerente01"));

        List<String> atendentes = new PedidoDashboardRepository(template).listarAtendentes();

        assertThat(atendentes).containsExactly("atendente01", "gerente01");
    }
}
