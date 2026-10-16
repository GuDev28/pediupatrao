package com.umc.pediupatrao.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.controller.AuditoriaController;
import com.umc.pediupatrao.controller.ClienteController;
import com.umc.pediupatrao.controller.PedidoController;
import com.umc.pediupatrao.controller.UsuarioController;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.ApiExceptionHandler;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.AcessoNegadoHandler;
import com.umc.pediupatrao.security.ApiAuthenticationEntryPoint;
import com.umc.pediupatrao.security.RespostaJson;
import com.umc.pediupatrao.security.UsuarioLogado;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Prova que as permissões estão no BACKEND: as requisições vão direto às APIs,
 * passando pela cadeia real do Spring Security (SecurityConfig), sem nenhuma tela.
 * Os serviços são simulados (mocks) para verificar se foram ou não chamados.
 */
@SpringJUnitWebConfig(SegurancaApiTest.Configuracao.class)
class SegurancaApiTest {

    private static final String SENHA = "senha123";
    private static final String CANCELAMENTO = "{\"justificativa\":\"Cliente desistiu do pedido\"}";
    private static final String DESCONTO = "{\"percentual\":10}";

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class, AcessoNegadoHandler.class, RespostaJson.class,
        UsuarioLogado.class, ApiExceptionHandler.class,
        PedidoController.class, AuditoriaController.class, UsuarioController.class, ClienteController.class})
    static class Configuracao {

        @Bean
        Clock clock() {
            return Clock.system(ZoneId.of("America/Sao_Paulo"));
        }

        @Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return Jackson2ObjectMapperBuilder.json().build();
        }

        /** Usuários de teste com senha BCrypt, um para cada perfil. */
        @Bean
        UsuarioDetailsService usuarioDetailsService() {
            UsuarioRepository repositorio = mock(UsuarioRepository.class);
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
            when(repositorio.findByUsername(anyString())).thenReturn(Optional.empty());
            when(repositorio.findByUsername("admin")).thenReturn(Optional.of(usuario("admin", Perfil.ADMIN, encoder)));
            when(repositorio.findByUsername("gerente01")).thenReturn(Optional.of(usuario("gerente01", Perfil.GERENTE, encoder)));
            when(repositorio.findByUsername("atendente01")).thenReturn(Optional.of(usuario("atendente01", Perfil.ATENDENTE, encoder)));
            return new UsuarioDetailsService(repositorio);
        }

        // Os mocks ficam em campos estáticos porque o Spring envolve os beans em proxies
        // (segurança por método); a verificação é feita diretamente nos mocks.
        static final PedidoService PEDIDOS = mock(PedidoService.class);
        static final AuditoriaService AUDITORIA = mock(AuditoriaService.class);
        static final UsuarioService USUARIOS = mock(UsuarioService.class);
        static final ClienteService CLIENTES = mock(ClienteService.class);

        @Bean
        PedidoService pedidoService() {
            return PEDIDOS;
        }

        @Bean
        AuditoriaService auditoriaService() {
            return AUDITORIA;
        }

        @Bean
        UsuarioService usuarioService() {
            return USUARIOS;
        }

        @Bean
        ClienteService clienteService() {
            return CLIENTES;
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

    private final PedidoService pedidoService = Configuracao.PEDIDOS;
    private final AuditoriaService auditoriaService = Configuracao.AUDITORIA;
    private final UsuarioService usuarioService = Configuracao.USUARIOS;
    private final ClienteService clienteService = Configuracao.CLIENTES;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        reset(pedidoService, auditoriaService, usuarioService, clienteService);
        Filter filtroSeguranca = contexto.getBean("springSecurityFilterChain", Filter.class);
        mvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(filtroSeguranca).build();
    }

    // ===================== autenticação =====================

    @Test
    void apiSemAutenticacaoResponde401EmJson() throws Exception {
        mvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void senhaErradaResponde401() throws Exception {
        mvc.perform(get("/api/pedidos").header("Authorization", basic("gerente01", "errada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cadastroDeUsuarioNaoEhMaisPublico() throws Exception {
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacker\",\"password\":\"123456\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").header("Authorization", basic("intruso", SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacker\",\"password\":\"123456\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
        verify(usuarioService, never()).salvarUsuario(any());
    }

    // ===================== RF01 / RF04 - cancelamento e desconto só do GERENTE =====================

    @Test
    void atendenteNaoCancelaNemAplicaDesconto() throws Exception {
        mvc.perform(json(post("/api/pedidos/p1/cancelar"), "atendente01", CANCELAMENTO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value(containsString("ATENDENTE")));
        mvc.perform(json(post("/api/pedidos/p1/desconto"), "atendente01", DESCONTO))
                .andExpect(status().isForbidden());

        verify(pedidoService, never()).cancelar(anyString(), anyString());
        verify(pedidoService, never()).aplicarDesconto(anyString(), any(), any());
        // a tentativa fica registrada na auditoria
        verify(auditoriaService).registrarAcessoNegado(eq("POST"), eq("/api/pedidos/p1/cancelar"), anyString());
    }

    @Test
    void adminTambemNaoCancelaNemAplicaDesconto() throws Exception {
        mvc.perform(json(post("/api/pedidos/p1/cancelar"), "admin", CANCELAMENTO))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/pedidos/p1/desconto"), "admin", DESCONTO))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).cancelar(anyString(), anyString());
    }

    @Test
    void gerenteCancelaEAplicaDesconto() throws Exception {
        when(pedidoService.cancelar(anyString(), anyString())).thenReturn(new Pedido());
        when(pedidoService.aplicarDesconto(anyString(), any(), any())).thenReturn(new Pedido());

        mvc.perform(json(post("/api/pedidos/p1/cancelar"), "gerente01", CANCELAMENTO))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/pedidos/p1/desconto"), "gerente01", DESCONTO))
                .andExpect(status().isOk());

        verify(pedidoService).cancelar("p1", "Cliente desistiu do pedido");
    }

    @Test
    void regraDeNegocioVioladaResponde422ComMensagem() throws Exception {
        when(pedidoService.aplicarDesconto(anyString(), any(), any()))
                .thenThrow(new RegraNegocioException("O desconto deve estar entre 0% e 20% do valor do pedido."));

        mvc.perform(json(post("/api/pedidos/p1/desconto"), "gerente01", "{\"percentual\":35}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(containsString("20%")));
    }

    // ===================== pedidos =====================

    @Test
    void adminConsultaPedidosMasNaoRegistraNemMudaStatus() throws Exception {
        when(pedidoService.listarPedidos()).thenReturn(new ArrayList<>());

        mvc.perform(get("/api/pedidos").header("Authorization", basic("admin", SENHA)))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/pedidos"), "admin", "{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/p1/status").param("status", "PRONTO")
                        .header("Authorization", basic("admin", SENHA)))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).criarPedido(any());
    }

    @Test
    void atendenteRegistraPedidoEAvancaStatus() throws Exception {
        when(pedidoService.criarPedido(any())).thenReturn(new Pedido());
        when(pedidoService.atualizarStatus(anyString(), anyString())).thenReturn(new Pedido());

        mvc.perform(json(post("/api/pedidos"), "atendente01",
                        "{\"clienteId\":\"c1\",\"tipoEntrega\":\"ENTREGA\",\"itens\":[{\"produtoId\":\"p\",\"quantidade\":1}]}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/pedidos/p1/status").param("status", "EM_PREPARACAO")
                        .header("Authorization", basic("atendente01", SENHA)))
                .andExpect(status().isOk());
    }

    // ===================== RF05 - auditoria =====================

    @Test
    void auditoriaSoParaAdminEGerente() throws Exception {
        when(auditoriaService.consultar(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(new ArrayList<>()));

        mvc.perform(get("/api/auditoria").header("Authorization", basic("atendente01", SENHA)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria").header("Authorization", basic("gerente01", SENHA)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").header("Authorization", basic("admin", SENHA)))
                .andExpect(status().isOk());
    }

    @Test
    void registrosDeAuditoriaNaoPodemSerAlteradosNemExcluidos() throws Exception {
        mvc.perform(delete("/api/auditoria/a1").header("Authorization", basic("admin", SENHA)))
                .andExpect(status().isForbidden());
        mvc.perform(json(put("/api/auditoria/a1"), "gerente01", "{}"))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/auditoria"), "gerente01", "{}"))
                .andExpect(status().isForbidden());
    }

    // ===================== usuários e clientes =====================

    @Test
    void somenteAdminGerenciaUsuariosESenhaNaoEhExposta() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setUsername("gerente01");
        usuario.setPassword("$2a$10$hashQueNaoPodeAparecer");
        usuario.setRole(Perfil.GERENTE);
        List<Usuario> usuarios = new ArrayList<>();
        usuarios.add(usuario);
        when(usuarioService.listarTodos()).thenReturn(usuarios);

        mvc.perform(get("/api/usuarios").header("Authorization", basic("gerente01", SENHA)))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/usuarios"), "gerente01",
                        "{\"username\":\"x\",\"password\":\"123456\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").header("Authorization", basic("admin", SENHA)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("hashQueNaoPodeAparecer"))))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void gerenteConsultaClientesMasSomenteAtendenteAltera() throws Exception {
        when(clienteService.listarClientes()).thenReturn(new ArrayList<>());

        mvc.perform(get("/api/clientes").header("Authorization", basic("gerente01", SENHA)))
                .andExpect(status().isOk());
        mvc.perform(json(put("/api/clientes/c1"), "gerente01", "{\"nome\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes").header("Authorization", basic("admin", SENHA)))
                .andExpect(status().isForbidden());
        verify(clienteService, never()).atualizar(anyString(), any());
    }

    // ===================== CSRF =====================

    @Test
    void requisicaoComSessaoDoNavegadorExigeTokenCsrf() throws Exception {
        MockHttpSession sessao = sessaoAutenticada("gerente01", Perfil.GERENTE);

        mvc.perform(post("/api/pedidos/p1/cancelar").session(sessao)
                        .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value(containsString("CSRF")));
        verify(pedidoService, never()).cancelar(anyString(), anyString());
    }

    @Test
    void requisicaoComSessaoETokenCsrfValidoEhAceita() throws Exception {
        when(pedidoService.cancelar(anyString(), anyString())).thenReturn(new Pedido());
        MockHttpSession sessao = sessaoAutenticada("gerente01", Perfil.GERENTE);
        String token = "token-csrf-de-teste";
        sessao.setAttribute(HttpSessionCsrfTokenRepository.class.getName().concat(".CSRF_TOKEN"),
                new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", token));

        mvc.perform(post("/api/pedidos/p1/cancelar").session(sessao)
                        .header("X-CSRF-TOKEN", mascararComoONavegador(token))
                        .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isOk());
    }

    // ===================== apoio =====================

    private static String basic(String usuario, String senha) {
        String credenciais = usuario + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(credenciais.getBytes(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder requisicao, String usuario,
            String corpo) {
        return requisicao.header("Authorization", basic(usuario, SENHA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    private static MockHttpSession sessaoAutenticada(String usuario, Perfil perfil) {
        List<SimpleGrantedAuthority> autoridades = new ArrayList<>();
        autoridades.add(new SimpleGrantedAuthority(perfil.getAutoridade()));
        SecurityContextImpl contexto = new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(usuario, null, autoridades));
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        return sessao;
    }

    /**
     * O Spring Security 6 entrega o token ao navegador "mascarado" (proteção BREACH):
     * bytes aleatórios + (aleatórios XOR token), em Base64. Aqui os bytes aleatórios são zeros.
     */
    private static String mascararComoONavegador(String token) {
        byte[] bytesToken = token.getBytes(StandardCharsets.UTF_8);
        byte[] combinado = new byte[bytesToken.length * 2];
        System.arraycopy(bytesToken, 0, combinado, bytesToken.length, bytesToken.length);
        return Base64.getUrlEncoder().encodeToString(combinado);
    }
}
