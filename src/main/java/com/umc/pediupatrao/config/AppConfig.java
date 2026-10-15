package com.umc.pediupatrao.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    public static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");

    /**
     * Relógio único da aplicação (horário de Brasília) usado nas datas/horas de entrada,
     * saída, cancelamento e auditoria. Injetável para facilitar os testes.
     */
    @Bean
    public Clock clock() {
        return Clock.system(FUSO_HORARIO);
    }
}
