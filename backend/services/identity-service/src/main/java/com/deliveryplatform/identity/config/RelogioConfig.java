package com.deliveryplatform.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * O relógio é injetado, nunca chamado direto.
 *
 * <p>A ADR-025 proíbe {@code LocalDate.now()} por causa de fuso. Aqui o motivo
 * é outro e vale igual: {@code Instant.now()} dentro de um caso de uso torna
 * intestável tudo que depende de tempo — e o código de verificação é inteiro
 * sobre tempo. Com o {@code Clock} na porta de entrada, "o código expirou" vira
 * um teste de unidade em vez de um {@code Thread.sleep}.
 *
 * <p>{@code systemUTC} e não {@code systemDefaultZone}: instante é instante, e
 * o fuso da máquina não tem nada a dizer sobre ele.
 */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }
}
