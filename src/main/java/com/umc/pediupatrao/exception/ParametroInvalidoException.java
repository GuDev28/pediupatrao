package com.umc.pediupatrao.exception;

/**
 * Parâmetro de consulta com formato ou valor inválido (data mal formatada, opção inexistente,
 * número fora do intervalo permitido). Responde HTTP 400 nas APIs.
 *
 * Diferente de RegraNegocioException (422), que indica parâmetros bem formados que violam
 * uma regra (ex.: data inicial depois da final).
 */
public class ParametroInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ParametroInvalidoException(String mensagem) {
        super(mensagem);
    }
}
