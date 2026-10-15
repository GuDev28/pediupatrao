package com.umc.pediupatrao.entity;

import java.util.Locale;

/**
 * Campo modificado em uma operação auditada, com valor anterior e novo.
 * Imutável: não possui setters.
 *
 * Campos de credenciais (senha, token etc.) nunca têm o valor registrado,
 * mesmo que algum serviço tente registrá-los por engano.
 */
public final class CampoAlterado {

    public static final String VALOR_PROTEGIDO = "[valor protegido]";

    private static final String[] TERMOS_SENSIVEIS = {"senha", "password", "token", "secret", "credencial"};

    private final String campo;
    private final String valorAnterior;
    private final String valorNovo;

    public CampoAlterado(String campo, String valorAnterior, String valorNovo) {
        this.campo = campo;
        if (isCampoSensivel(campo)) {
            this.valorAnterior = valorAnterior == null ? null : VALOR_PROTEGIDO;
            this.valorNovo = valorNovo == null ? null : VALOR_PROTEGIDO;
        } else {
            this.valorAnterior = valorAnterior;
            this.valorNovo = valorNovo;
        }
    }

    public static boolean isCampoSensivel(String campo) {
        if (campo == null) {
            return false;
        }
        String nome = campo.toLowerCase(Locale.ROOT);
        for (String termo : TERMOS_SENSIVEIS) {
            if (nome.contains(termo)) {
                return true;
            }
        }
        return false;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }
}
