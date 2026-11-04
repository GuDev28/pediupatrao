package com.umc.pediupatrao.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.umc.pediupatrao.dto.dashboard.DimensaoDistribuicao;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** RF05/RF07 - validação e normalização dos filtros do dashboard. */
class CriteriosDashboardTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 3);

    @Test
    void semDatasUsaOsUltimos30DiasIncluindoHoje() {
        CriteriosDashboard criterios = CriteriosDashboard.de(new FiltroDashboard(), HOJE);

        assertThat(criterios.dataInicio()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(criterios.dataFim()).isEqualTo(HOJE);
        assertThat(criterios.dias()).isEqualTo(30);
        assertThat(CriteriosDashboard.de(null, HOJE).dias()).isEqualTo(30);
    }

    @Test
    void datasSaoInclusivasEOFimEhExclusivoNaConsulta() {
        CriteriosDashboard criterios = CriteriosDashboard.de(filtro("2026-10-01", "2026-10-31"), HOJE);

        assertThat(criterios.inicio()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(criterios.fimExclusivo()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(criterios.dias()).isEqualTo(31);
    }

    @Test
    void periodoAnteriorTemAMesmaDuracaoETerminaNaVespera() {
        CriteriosDashboard anterior = CriteriosDashboard.de(filtro("2026-10-01", "2026-10-31"), HOJE).anterior();

        assertThat(anterior.dataInicio()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(anterior.dataFim()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(anterior.dias()).isEqualTo(31);
    }

    @Test
    void completaADataQueFaltar() {
        assertThat(CriteriosDashboard.de(filtro("2026-10-20", null), HOJE).dataFim()).isEqualTo(HOJE);
        assertThat(CriteriosDashboard.de(filtro("2026-12-01", null), HOJE).dataFim())
                .isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(CriteriosDashboard.de(filtro(null, "2026-10-30"), HOJE).dataInicio())
                .isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void dataInicialDepoisDaFinalViolaRegraDeNegocio() {
        assertThatThrownBy(() -> CriteriosDashboard.de(filtro("2026-10-10", "2026-10-01"), HOJE))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não pode ser posterior");
    }

    @Test
    void periodoMaiorQue366DiasViolaRegraDeNegocio() {
        assertThatThrownBy(() -> CriteriosDashboard.de(filtro("2025-01-01", "2026-01-02"), HOJE))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("366");
        assertThat(CriteriosDashboard.de(filtro("2025-01-01", "2026-01-01"), HOJE).dias()).isEqualTo(366);
    }

    @Test
    void dataMalFormatadaEhParametroInvalido() {
        assertThatThrownBy(() -> CriteriosDashboard.de(filtro("03/11/2026", null), HOJE))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("dataInicio")
                .hasMessageContaining("aaaa-mm-dd");
        assertThatThrownBy(() -> CriteriosDashboard.de(filtro(null, "2026-02-30"), HOJE))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void tipoDeEntregaEAtendenteSaoNormalizados() {
        FiltroDashboard filtro = new FiltroDashboard(null, null, " entrega ", "  gerente01 ");

        CriteriosDashboard criterios = CriteriosDashboard.de(filtro, HOJE);

        assertThat(criterios.tipoEntrega()).isEqualTo(TipoEntrega.ENTREGA);
        assertThat(criterios.atendente()).isEqualTo("gerente01");
        assertThat(CriteriosDashboard.de(new FiltroDashboard(null, null, "", " "), HOJE).atendente()).isNull();
    }

    @Test
    void opcaoInexistenteListaOsValoresAceitos() {
        assertThatThrownBy(() -> CriteriosDashboard.de(new FiltroDashboard(null, null, "DRONE", null), HOJE))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("tipoEntrega")
                .hasMessageContaining("ENTREGA, RETIRADA");
    }

    @Test
    void leitorAceitaVariacoesDeDigitacaoNasOpcoes() {
        assertThat(LeitorParametros.opcao("dia-semana", DimensaoDistribuicao.class, "dimensao"))
                .isEqualTo(DimensaoDistribuicao.DIA_SEMANA);
        assertThat(LeitorParametros.opcao("Em preparação", StatusPedido.class, "status"))
                .isEqualTo(StatusPedido.EM_PREPARACAO);
        assertThat(LeitorParametros.opcao(null, StatusPedido.class, "status")).isNull();
    }

    @Test
    void leitorValidaIntervaloDosNumeros() {
        assertThat(LeitorParametros.inteiro(null, 10, 1, 50, "limite")).isEqualTo(10);
        assertThat(LeitorParametros.inteiro(50, 10, 1, 50, "limite")).isEqualTo(50);
        assertThatThrownBy(() -> LeitorParametros.inteiro(0, 10, 1, 50, "limite"))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("entre 1 e 50");
    }

    private static FiltroDashboard filtro(String inicio, String fim) {
        return new FiltroDashboard(inicio, fim, null, null);
    }
}
