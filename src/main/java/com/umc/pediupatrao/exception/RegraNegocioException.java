package com.umc.pediupatrao.exception;

/**
 * Violação de uma regra de negócio (transição inválida, desconto acima do limite,
 * pedido cancelado, dados obrigatórios ausentes etc.). Responde HTTP 422 nas APIs.
 */
public class RegraNegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
