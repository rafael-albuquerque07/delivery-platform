package com.deliveryplatform.merchant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * O relógio, como dependência e não como chamada estática.
 *
 * <p>{@code Instant.now()} espalhado pelos serviços é a forma mais comum de
 * escrever uma regra de tempo que não dá para testar sem dormir. Com o relógio
 * injetado, um teste que precisa de "sete dias depois" usa
 * {@code Clock.offset(...)} e roda em milissegundos.
 *
 * <p>Fica em UTC. O fuso do estabelecimento é dado de domínio (ADR-025) e mora
 * no agregado; o relógio do processo não tem opinião sobre isso.
 */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }
}
