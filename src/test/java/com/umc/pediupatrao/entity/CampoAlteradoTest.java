package com.umc.pediupatrao.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CampoAlteradoTest {

    @Test
    void nuncaGuardaValoresDeSenhaOuToken() {
        CampoAlterado senha = new CampoAlterado("senha", "123456", "novaSenha");
        CampoAlterado token = new CampoAlterado("tokenAcesso", null, "abc");

        assertThat(senha.getValorAnterior()).isEqualTo(CampoAlterado.VALOR_PROTEGIDO);
        assertThat(senha.getValorNovo()).isEqualTo(CampoAlterado.VALOR_PROTEGIDO);
        assertThat(token.getValorAnterior()).isNull();
        assertThat(token.getValorNovo()).isEqualTo(CampoAlterado.VALOR_PROTEGIDO);
    }

    @Test
    void camposComunsGuardamValoresAnteriorENovo() {
        CampoAlterado logradouro = new CampoAlterado("logradouro", "Rua das Flores", "Av. Brasil");

        assertThat(logradouro.getValorAnterior()).isEqualTo("Rua das Flores");
        assertThat(logradouro.getValorNovo()).isEqualTo("Av. Brasil");
    }
}
