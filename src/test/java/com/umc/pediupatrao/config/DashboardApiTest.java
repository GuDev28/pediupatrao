package com.umc.pediupatrao.config;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.umc.pediupatrao.controller.DashboardController;
import com.umc.pediupatrao.controller.DashboardWebController;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.Indicador;
import com.umc.pediupatrao.dto.dashboard.Periodo;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasResponse;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.ApiExceptionHandler;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.AcessoNegadoHandler;
import com.umc.pediupatrao.security.ApiAuthenticationEntryPoint;
import com.umc.pediupatrao.security.RespostaJson;
import com.umc.pediupatrao.security.UsuarioLogado;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API do dashboard passando pela cadeia real do Spring Security (SecurityConfig):
 * autenticação, perfis autorizados, somente leitura, contrato JSON e respostas de erro.
 * O DashboardService é simulado; os cálculos são testados em CalculadoraDashboardTest.
 */
@SpringJUnitWebConfig(DashboardApiTest.Configuracao.class)
class DashboardApiTest {

    private static final String SENHA = "senha123";

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class, AcessoNegadoHandler.class, RespostaJson.class,
        UsuarioLogado.class, ApiExceptionHandler.class, DashboardController.class, DashboardWebController.class})
    static class Configuracao implements WebMvcConfigurer {

        static final DashboardService DASHBOARD = mock(DashboardService.class);
        static final AuditoriaService AUDITORIA = mock(AuditoriaService.class);

        @Bean
        Clock clock() {
            return Clock.system(ZoneId.of("America/Sao_Paulo"));
        }

        /** Mesma configuração do Spring Boot: datas em texto ISO (ex.: "2026-10-05"), não em lista de números. */
        @Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return Jackson2ObjectMapperBuilder.json()
                    .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        }

        @Override
        public void configureMessageConverters(List<HttpMessageConverter<?>> conversores) {
            conversores.add(new MappingJackson2HttpMessageConverter(objectMapper()));
        }

        @Bean
        UsuarioDetailsService usuarioDetailsService() {
            UsuarioRepository repositorio = mock(UsuarioRepository.class);
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
            when(repositorio.findByUsername(anyString())).thenReturn(Optional.empty());
            when(repositorio.findByUsername("admin")).thenReturn(Optional.of(usuario("admin", Perfil.ADMIN, encoder)));
            when(repositorio.findByUsername("gerente01"))
                    .thenReturn(Optional.of(usuario("gerente01", Perfil.GERENTE, encoder)));
            when(repositorio.findByUsername("atendente01"))
                    .thenReturn(Optional.of(usuario("atendente01", Perfil.ATENDENTE, encoder)));
            return new UsuarioDetailsService(repositorio);
        }

        @Bean
        DashboardService dashboardService() {
            return DASHBOARD;
        }

        @Bean
        AuditoriaService auditoriaService() {
            return AUDITORIA;
        }

        private static Usuario usuario(String username, Perfil perfil, BCryptPasswordEncoder encoder) {
            Usuario usuario = new Usuario();
            usuario.setId("id-" + username);
            usuario.setUsername(username);
            usuario.setPassword(encoder.encode(SENHA));
            usuario.setRole(perfil);
            return usuario;
        }
    }

    @Autowired
    private WebApplicationContext contexto;

    private final DashboardService dashboardService = Configuracao.DASHBOARD;
    private final AuditoriaService auditoriaService = Configuracao.AUDITORIA;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        reset(dashboardService, auditoriaService);
        Filter filtroSeguranca = contexto.getBean("springSecurityFilterChain", Filter.class);
        mvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtroSeguranca).build();
    }

    // ===================== autenticação e perfis =====================

    @Test
    void semAutenticacaoResponde401EmJson() throws Exception {
        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verify(dashboardService, never()).resumo(any());
    }

    @Test
    void atendenteNaoAcessaODashboardEATentativaEhAuditada() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").header("Authorization", basic("atendente01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value(containsString("ATENDENTE")));
        mvc.perform(get("/dashboard").header("Authorization", basic("atendente01")))
                .andExpect(status().isForbidden());

        verify(dashboardService, never()).resumo(any());
        verify(auditoriaService).registrarAcessoNegado(eq("GET"), eq("/api/dashboard/resumo"), anyString());
    }

    @Test
    void gerenteEAdminConsultamOResumoEmJson() throws Exception {
        when(dashboardService.resumo(any())).thenReturn(resumoDeExemplo());

        for (String usuario : new String[] {"gerente01", "admin"}) {
            mvc.perform(get("/api/dashboard/resumo").header("Authorization", basic(usuario)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodo.dataInicio").value("2026-10-05"))
                    .andExpect(jsonPath("$.periodo.dias").value(30))
                    .andExpect(jsonPath("$.faturamento.atual").value(1234.5))
                    .andExpect(jsonPath("$.faturamento.variacaoPercentual").value(12.2))
                    .andExpect(jsonPath("$.pedidos.variacaoPercentual").doesNotExist())
                    .andExpect(jsonPath("$.taxaCancelamento").value(2.5))
                    .andExpect(jsonPath("$.geradoEm").value("2026-11-03T16:30:00"));
        }
    }

    @Test
    void respostasDoDashboardNaoSaoGuardadasEmCache() throws Exception {
        when(dashboardService.resumo(any())).thenReturn(resumoDeExemplo());

        mvc.perform(get("/api/dashboard/resumo").header("Authorization", basic("gerente01")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void dashboardEhSomenteLeitura() throws Exception {
        mvc.perform(post("/api/dashboard/resumo").header("Authorization", basic("gerente01")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/dashboard/filtros").header("Authorization", basic("admin")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/dashboard/pedidos").header("Authorization", basic("gerente01")))
                .andExpect(status().isForbidden());
    }

    @Test
    void telaDoDashboardUsaOLayoutDoSistema() throws Exception {
        mvc.perform(get("/dashboard").header("Authorization", basic("gerente01")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("layout"))
                .andExpect(model().attribute("content", "dashboard :: content"));
    }

    // ===================== parâmetros =====================

    @Test
    void filtrosDaUrlChegamAoServico() throws Exception {
        when(dashboardService.resumo(any())).thenReturn(resumoDeExemplo());

        mvc.perform(get("/api/dashboard/resumo").header("Authorization", basic("gerente01"))
                        .param("dataInicio", "2026-10-01").param("dataFim", "2026-10-31")
                        .param("tipoEntrega", "ENTREGA").param("atendente", "atendente01"))
                .andExpect(status().isOk());

        ArgumentCaptor<FiltroDashboard> filtro = ArgumentCaptor.forClass(FiltroDashboard.class);
        verify(dashboardService).resumo(filtro.capture());
        org.assertj.core.api.Assertions.assertThat(filtro.getValue().getDataInicio()).isEqualTo("2026-10-01");
        org.assertj.core.api.Assertions.assertThat(filtro.getValue().getDataFim()).isEqualTo("2026-10-31");
        org.assertj.core.api.Assertions.assertThat(filtro.getValue().getTipoEntrega()).isEqualTo("ENTREGA");
        org.assertj.core.api.Assertions.assertThat(filtro.getValue().getAtendente()).isEqualTo("atendente01");
    }

    @Test
    void todosOsEndpointsRespondemComOsParametrosEspecificos() throws Exception {
        String gerente = basic("gerente01");
        mvc.perform(get("/api/dashboard/filtros").header("Authorization", gerente)).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/vendas/serie").param("agrupamento", "SEMANA").header("Authorization", gerente))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/vendas/distribuicao").param("dimensao", "HORA")
                .header("Authorization", gerente)).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/produtos/mais-vendidos").param("categoria", "Pizza")
                .param("ordenarPor", "FATURAMENTO").param("limite", "5").header("Authorization", gerente))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/pedidos/situacao").header("Authorization", gerente))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/pedidos/em-aberto").param("limiteMinutos", "30")
                .header("Authorization", gerente)).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/pedidos").param("status", "CANCELADO").param("pagina", "1")
                .param("tamanho", "20").header("Authorization", gerente)).andExpect(status().isOk());

        verify(dashboardService).opcoesDeFiltro();
        verify(dashboardService).serieDeVendas(any(), eq("SEMANA"));
        verify(dashboardService).distribuicao(any(), eq("HORA"));
        verify(dashboardService).produtosMaisVendidos(any(), eq("Pizza"), eq("FATURAMENTO"), eq(5));
        verify(dashboardService).situacaoDosPedidos(any());
        verify(dashboardService).pedidosEmAberto(isNull(), eq(30), isNull());
        verify(dashboardService).listarPedidos(any(), eq("CANCELADO"), eq(1), eq(20));
    }

    // ===================== erros =====================

    @Test
    void periodoInvalidoResponde422ComMensagem() throws Exception {
        when(dashboardService.resumo(any())).thenThrow(new RegraNegocioException(
                "A data inicial (2026-11-03) não pode ser posterior à data final (2026-10-01)."));

        mvc.perform(get("/api/dashboard/resumo").header("Authorization", basic("gerente01"))
                        .param("dataInicio", "2026-11-03").param("dataFim", "2026-10-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.mensagem").value(containsString("posterior")))
                .andExpect(jsonPath("$.caminho").value("/api/dashboard/resumo"));
    }

    @Test
    void parametroInvalidoResponde400ComMensagem() throws Exception {
        when(dashboardService.serieDeVendas(any(), eq("ANO"))).thenThrow(new ParametroInvalidoException(
                "Valor inválido para 'agrupamento': 'ANO'. Valores aceitos: DIA, SEMANA, MES."));

        mvc.perform(get("/api/dashboard/vendas/serie").param("agrupamento", "ANO")
                        .header("Authorization", basic("gerente01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("DIA, SEMANA, MES")));
    }

    @Test
    void numeroMalFormatadoResponde400() throws Exception {
        mvc.perform(get("/api/dashboard/produtos/mais-vendidos").param("limite", "dez")
                        .header("Authorization", basic("gerente01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value(containsString("'limite'")));
        verify(dashboardService, never()).produtosMaisVendidos(any(), any(), any(), any());
    }

    // ===================== apoio =====================

    private static ResumoVendasResponse resumoDeExemplo() {
        Periodo periodo = new Periodo(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 3), 30);
        Periodo anterior = new Periodo(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 4), 30);
        return new ResumoVendasResponse(periodo, anterior,
                new Indicador(new BigDecimal("1234.50"), new BigDecimal("1100.00"), new BigDecimal("12.2")),
                new Indicador(new BigDecimal("40"), BigDecimal.ZERO, null),
                new Indicador(new BigDecimal("30.86"), new BigDecimal("0.00"), null),
                new Indicador(new BigDecimal("95"), BigDecimal.ZERO, null),
                1, new BigDecimal("2.5"), new BigDecimal("54.90"), 3, new BigDecimal("18.30"), 2,
                LocalDateTime.of(2026, 11, 3, 16, 30));
    }

    private static String basic(String usuario) {
        String credenciais = usuario + ":" + SENHA;
        return "Basic " + Base64.getEncoder().encodeToString(credenciais.getBytes(StandardCharsets.UTF_8));
    }
}
