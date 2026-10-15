package com.umc.pediupatrao.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umc.pediupatrao.dto.ErroResposta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Escreve respostas de erro em JSON para as rotas /api/** (401 e 403). */
@Component
public class RespostaJson {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RespostaJson(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public static boolean isRequisicaoApi(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contexto = request.getContextPath() == null ? "" : request.getContextPath();
        return uri != null && uri.startsWith(contexto + "/api/");
    }

    public void escrever(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            String mensagem) throws IOException {
        ErroResposta corpo = new ErroResposta(status.value(), status.getReasonPhrase(), mensagem,
                request.getRequestURI(), LocalDateTime.now(clock));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), corpo);
    }
}
