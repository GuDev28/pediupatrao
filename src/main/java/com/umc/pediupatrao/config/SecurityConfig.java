package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.security.AcessoNegadoHandler;
import com.umc.pediupatrao.security.ApiAuthenticationEntryPoint;
import com.umc.pediupatrao.security.RespostaJson;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Autenticação e autorização (RF01).
 *
 * As permissões são aplicadas em duas camadas no backend:
 * 1) aqui, por rota e método HTTP (telas e APIs), negando tudo que não estiver liberado;
 * 2) nos serviços, com @PreAuthorize (ex.: PedidoService.cancelar exige GERENTE).
 * Ocultar botões nas telas é apenas conveniência visual.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = Perfil.ADMIN.name();
    private static final String GERENTE = Perfil.GERENTE.name();
    private static final String ATENDENTE = Perfil.ATENDENTE.name();

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /** Permite encerrar as sessões de um usuário quando o ADMIN altera o perfil dele ou o exclui. */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            ApiAuthenticationEntryPoint apiEntryPoint,
            AcessoNegadoHandler acessoNegadoHandler,
            SessionRegistry sessionRegistry) throws Exception {
        http
                // CSRF ATIVO para formulários e chamadas feitas pelo navegador (sessão/cookie).
                // Só fica isento quem chama /api/** com cabeçalho Authorization (ex.: curl/Postman com
                // HTTP Basic): o navegador não envia esse cabeçalho sozinho em requisições de outro site.
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(exigeCsrf()))
                .authorizeHttpRequests(auth -> auth
                        // Público
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**",
                                "/js/**", "/images/**", "/favicon.ico").permitAll()

                        // Usuários e perfis: somente ADMIN (antes /api/usuarios era público!)
                        .requestMatchers("/usuarios", "/usuarios/**", "/api/usuarios", "/api/usuarios/**").hasRole(ADMIN)
                        .requestMatchers("/admin", "/configuracoes").hasRole(ADMIN)

                        // Auditoria: somente leitura, para ADMIN e GERENTE. Qualquer outro método é negado.
                        .requestMatchers(HttpMethod.GET, "/auditoria", "/auditoria/**",
                                "/api/auditoria", "/api/auditoria/**").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers("/auditoria", "/auditoria/**", "/api/auditoria", "/api/auditoria/**").denyAll()

                        // Cancelamento e desconto: exclusivos do GERENTE (inclusive em relação ao ADMIN)
                        .requestMatchers("/pedidos/*/cancelar", "/pedidos/*/desconto",
                                "/api/pedidos/*/cancelar", "/api/pedidos/*/desconto").hasRole(GERENTE)

                        // Pedidos
                        .requestMatchers("/pedidos/novo", "/pedidos/*/editar").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.GET, "/pedidos", "/pedidos/**",
                                "/api/pedidos", "/api/pedidos/**").hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/pedidos", "/pedidos/**", "/api/pedidos", "/api/pedidos/**")
                                .hasAnyRole(GERENTE, ATENDENTE)

                        // Clientes: ATENDENTE cadastra/atualiza; GERENTE consulta (e pode excluir)
                        .requestMatchers("/clientes/novo", "/clientes/salvar", "/clientes/editar/**").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes", "/api/clientes/**")
                                .hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/api/clientes").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/**").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole(GERENTE)

                        // Produtos (informações comerciais): todos consultam; GERENTE mantém
                        .requestMatchers(HttpMethod.GET, "/produtos", "/api/produtos", "/api/produtos/**")
                                .hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/produtos/**", "/api/produtos", "/api/produtos/**").hasRole(GERENTE)

                        .requestMatchers("/").authenticated()

                        // Negação por padrão: rota não listada acima não é acessível.
                        .anyRequest().denyAll()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                // HTTP Basic apenas para clientes de API (curl/Postman). Sem desafio WWW-Authenticate.
                .httpBasic(basic -> basic.authenticationEntryPoint(apiEntryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(pontoDeEntrada(apiEntryPoint))
                        .accessDeniedHandler(acessoNegadoHandler)
                )
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/login?expirada")
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect(request.getContextPath() + "/"); // Página inicial após login bem-sucedido
        };
    }

    /** APIs respondem 401 em JSON; telas redirecionam para o login. */
    private AuthenticationEntryPoint pontoDeEntrada(ApiAuthenticationEntryPoint apiEntryPoint) {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> pontos = new LinkedHashMap<>();
        pontos.put(RespostaJson::isRequisicaoApi, apiEntryPoint);
        DelegatingAuthenticationEntryPoint delegating = new DelegatingAuthenticationEntryPoint(pontos);
        delegating.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        return delegating;
    }

    /**
     * Define quando o token CSRF é exigido: em todo método que altera estado
     * (POST, PUT, PATCH, DELETE), exceto chamadas a /api/** autenticadas por cabeçalho
     * Authorization, que não dependem de cookie e por isso não sofrem CSRF.
     */
    static RequestMatcher exigeCsrf() {
        return (HttpServletRequest request) -> {
            String metodo = request.getMethod();
            if ("GET".equals(metodo) || "HEAD".equals(metodo) || "OPTIONS".equals(metodo)
                    || "TRACE".equals(metodo)) {
                return false;
            }
            String authorization = request.getHeader("Authorization");
            boolean credencialExplicita = authorization != null
                    && authorization.regionMatches(true, 0, "Basic ", 0, 6);
            return !(RespostaJson.isRequisicaoApi(request) && credencialExplicita);
        };
    }
}
