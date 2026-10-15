package com.umc.pediupatrao.security;

import com.umc.pediupatrao.service.AuditoriaService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * Tratamento das tentativas de acesso indevido (HTTP 403).
 *
 * - Registra a tentativa no log e, para usuários autenticados, na auditoria
 *   (operação ACESSO_NEGADO), sem gravar senhas, tokens ou cabeçalhos.
 * - APIs (/api/**) recebem JSON; telas recebem a página de erro 403.
 */
@Component
public class AcessoNegadoHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(AcessoNegadoHandler.class);

    private final AuditoriaService auditoriaService;
    private final UsuarioLogado usuarioLogado;
    private final RespostaJson respostaJson;
    private final AccessDeniedHandler paginaDeErro = new AccessDeniedHandlerImpl();

    public AcessoNegadoHandler(AuditoriaService auditoriaService, UsuarioLogado usuarioLogado,
            RespostaJson respostaJson) {
        this.auditoriaService = auditoriaService;
        this.usuarioLogado = usuarioLogado;
        this.respostaJson = respostaJson;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException excecao) throws IOException, ServletException {

        boolean falhaCsrf = excecao instanceof CsrfException;
        String motivo = falhaCsrf
                ? "Token CSRF ausente ou inválido"
                : "Perfil sem permissão para esta operação";

        log.warn("Acesso negado: usuario={} perfil={} {} {} ({})", usuarioLogado.getUsername(),
                usuarioLogado.getNomePerfil(), request.getMethod(), request.getRequestURI(), motivo);

        if (usuarioLogado.isAutenticado()) {
            try {
                auditoriaService.registrarAcessoNegado(request.getMethod(), request.getRequestURI(), motivo);
            } catch (RuntimeException e) {
                // A falha ao auditar não pode liberar o acesso nem esconder o 403.
                log.error("Não foi possível registrar a tentativa de acesso negado na auditoria", e);
            }
        }

        if (RespostaJson.isRequisicaoApi(request)) {
            String mensagem = falhaCsrf
                    ? "Acesso negado: token CSRF ausente ou inválido."
                    : "Acesso negado: o perfil " + usuarioLogado.getNomePerfil()
                            + " não tem permissão para esta operação.";
            respostaJson.escrever(request, response, HttpStatus.FORBIDDEN, mensagem);
        } else {
            paginaDeErro.handle(request, response, excecao);
        }
    }
}
