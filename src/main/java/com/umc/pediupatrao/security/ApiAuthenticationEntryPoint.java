package com.umc.pediupatrao.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Resposta 401 em JSON para requisições às APIs sem autenticação (ou com credenciais
 * inválidas), em vez de redirecionar para a tela de login.
 *
 * Não envia o cabeçalho "WWW-Authenticate: Basic" de propósito: assim o navegador
 * nunca abre a janela de login HTTP nem memoriza credenciais Basic, o que mantém
 * a isenção de CSRF para chamadas com cabeçalho Authorization segura (ver SecurityConfig).
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final RespostaJson respostaJson;

    public ApiAuthenticationEntryPoint(RespostaJson respostaJson) {
        this.respostaJson = respostaJson;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        respostaJson.escrever(request, response, HttpStatus.UNAUTHORIZED,
                "Autenticação necessária. Faça login ou envie credenciais válidas.");
    }
}
