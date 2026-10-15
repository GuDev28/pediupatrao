package com.umc.pediupatrao.exception;

/**
 * Recurso inexistente (pedido, cliente, produto ou usuário). Responde HTTP 404 nas APIs.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
