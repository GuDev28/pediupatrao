package com.umc.pediupatrao.exception;

import com.umc.pediupatrao.dto.ErroResposta;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Converte as exceções das APIs REST em respostas JSON padronizadas.
 *
 * AccessDeniedException NÃO é tratada aqui de propósito: ela segue para o
 * Spring Security, que responde 403 e registra a tentativa na auditoria.
 */
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResposta> regraNegocio(RegraNegocioException ex, HttpServletRequest request) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    public ResponseEntity<ErroResposta> parametroInvalido(ParametroInvalidoException ex, HttpServletRequest request) {
        return resposta(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> naoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest request) {
        return resposta(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErroResposta> conflito(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return resposta(HttpStatus.CONFLICT,
                "O registro foi alterado por outro usuário ao mesmo tempo. Recarregue e tente novamente.", request);
    }

    /** Parâmetro da URL com tipo errado (ex.: limite=dez): informa qual parâmetro corrigir. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResposta> tipoDeParametroInvalido(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        String esperado = ex.getRequiredType() != null && Number.class.isAssignableFrom(ex.getRequiredType())
                ? " (esperado um número inteiro)" : "";
        return resposta(HttpStatus.BAD_REQUEST, "Valor inválido para o parâmetro '" + ex.getName() + "': '"
                + ex.getValue() + "'" + esperado + ".", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErroResposta> requisicaoInvalida(Exception ex, HttpServletRequest request) {
        log.debug("Requisição inválida em {}: {}", request.getRequestURI(), ex.getMessage());
        return resposta(HttpStatus.BAD_REQUEST,
                "Requisição inválida: verifique o formato do JSON, os valores enumerados e os parâmetros obrigatórios.",
                request);
    }

    private ResponseEntity<ErroResposta> resposta(HttpStatus status, String mensagem, HttpServletRequest request) {
        ErroResposta corpo = new ErroResposta(status.value(), status.getReasonPhrase(), mensagem,
                request.getRequestURI(), LocalDateTime.now(clock));
        return ResponseEntity.status(status).body(corpo);
    }
}
