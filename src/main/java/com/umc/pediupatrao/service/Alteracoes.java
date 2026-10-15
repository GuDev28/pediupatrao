package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.CampoAlterado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Monta a lista de campos alterados (valor anterior x novo) para a auditoria.
 * Os valores são convertidos para texto de forma padronizada antes da comparação,
 * evitando falsas diferenças (ex.: 10.0 e 10.00, "" e null).
 */
public class Alteracoes {

    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final List<CampoAlterado> campos = new ArrayList<>();

    /** Registra o campo somente se o valor mudou. */
    public Alteracoes comparar(String campo, Object anterior, Object novo) {
        String textoAnterior = comoTexto(anterior);
        String textoNovo = comoTexto(novo);
        if (!Objects.equals(textoAnterior, textoNovo)) {
            campos.add(new CampoAlterado(campo, textoAnterior, textoNovo));
        }
        return this;
    }

    /** Registra o campo sempre (usado em inclusões, onde o valor anterior é vazio). */
    public Alteracoes incluir(String campo, Object novo) {
        String textoNovo = comoTexto(novo);
        if (textoNovo != null) {
            campos.add(new CampoAlterado(campo, null, textoNovo));
        }
        return this;
    }

    /** Registra o campo removido (usado em exclusões). */
    public Alteracoes remover(String campo, Object anterior) {
        String textoAnterior = comoTexto(anterior);
        if (textoAnterior != null) {
            campos.add(new CampoAlterado(campo, textoAnterior, null));
        }
        return this;
    }

    public boolean isVazia() {
        return campos.isEmpty();
    }

    public int getQuantidade() {
        return campos.size();
    }

    public List<CampoAlterado> getCampos() {
        return new ArrayList<>(campos);
    }

    static String comoTexto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof BigDecimal) {
            return ((BigDecimal) valor).setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        if (valor instanceof Double) {
            return BigDecimal.valueOf((Double) valor).setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        if (valor instanceof LocalDateTime) {
            return ((LocalDateTime) valor).format(FORMATO_DATA_HORA);
        }
        if (valor instanceof Enum<?>) {
            return ((Enum<?>) valor).name();
        }
        String texto = valor.toString().trim();
        return texto.isEmpty() ? null : texto;
    }
}
