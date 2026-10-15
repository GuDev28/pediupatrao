package com.umc.pediupatrao.dto;

import java.time.LocalDateTime;

/** Corpo JSON padronizado das respostas de erro das APIs. */
public record ErroResposta(int status, String erro, String mensagem, String caminho, LocalDateTime dataHora) {
}
