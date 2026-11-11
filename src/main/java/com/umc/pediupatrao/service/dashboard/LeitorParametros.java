package com.umc.pediupatrao.service.dashboard;

import com.umc.pediupatrao.exception.ParametroInvalidoException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Conversão dos parâmetros de texto recebidos pela API do dashboard (RF05/RF07).
 *
 * Aceita variações comuns de digitação nas opções (ex.: "entrega", "Dia-Semana",
 * "em preparação") e responde com mensagens que dizem exatamente o que está errado.
 * Valores vazios são tratados como "não informado".
 */
public final class LeitorParametros {

    private LeitorParametros() {
    }

    /** Converte uma data yyyy-MM-dd; vazio resulta em null. */
    public static LocalDate data(String valor, String nomeParametro) {
        String texto = limpar(valor);
        if (texto == null) {
            return null;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw new ParametroInvalidoException("O parâmetro '" + nomeParametro + "' deve ser uma data válida "
                    + "no formato aaaa-mm-dd (recebido: '" + texto + "').");
        }
    }

    /** Converte o texto em uma constante do enum (sem diferenciar maiúsculas, acentos, espaços ou hífens). */
    public static <E extends Enum<E>> E opcao(String valor, Class<E> tipo, String nomeParametro) {
        String texto = limpar(valor);
        if (texto == null) {
            return null;
        }
        String normalizado = normalizar(texto);
        E[] constantes = tipo.getEnumConstants();
        for (E constante : constantes) {
            if (constante.name().equals(normalizado)) {
                return constante;
            }
        }
        StringBuilder aceitos = new StringBuilder();
        for (E constante : constantes) {
            if (aceitos.length() > 0) {
                aceitos.append(", ");
            }
            aceitos.append(constante.name());
        }
        throw new ParametroInvalidoException("Valor inválido para '" + nomeParametro + "': '" + texto
                + "'. Valores aceitos: " + aceitos + ".");
    }

    /** Número inteiro opcional dentro de um intervalo; null resulta no valor padrão. */
    public static int inteiro(Integer valor, int padrao, int minimo, int maximo, String nomeParametro) {
        if (valor == null) {
            return padrao;
        }
        if (valor < minimo || valor > maximo) {
            throw new ParametroInvalidoException("O parâmetro '" + nomeParametro + "' deve estar entre "
                    + minimo + " e " + maximo + " (recebido: " + valor + ").");
        }
        return valor;
    }

    /** Remove espaços das pontas; texto vazio vira null. */
    public static String limpar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.isEmpty() ? null : limpo;
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replaceAll("[\\s\\-/]+", "_");
    }
}
