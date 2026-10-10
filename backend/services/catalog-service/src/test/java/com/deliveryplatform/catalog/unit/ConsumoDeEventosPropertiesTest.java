package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.config.ConsumoDeEventosProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Caso 6 da G-C3b: os padrões são a ADR-026.
 *
 * <p>O {@code ConsumoDeExpedienteIT} sobrescreve os números para milissegundos, então
 * nenhum teste de integração olha para 1 s, 4 s e 16 s. Este liga as propriedades pelo
 * mesmo {@code Binder} que o Spring Boot usa, sem nenhuma chave — o que sai são os
 * {@code @DefaultValue}.
 */
class ConsumoDeEventosPropertiesTest {

    private static ConsumoDeEventosProperties ligar(Map<String, String> chaves) {
        return new Binder(new MapConfigurationPropertySource(chaves))
                .bindOrCreate("delivery.consumo-de-eventos", ConsumoDeEventosProperties.class);
    }

    @Test
    @DisplayName("6 · sem configuração, os números são os da ADR-026: 1 s, ×4 até 16 s, quatro tentativas")
    void os_padroes_sao_a_adr_026() {
        ConsumoDeEventosProperties padrao = ligar(Map.of());

        assertThat(padrao.intervalo()).isEqualTo(Duration.ofSeconds(1));
        assertThat(padrao.multiplicador()).isEqualTo(4.0);
        assertThat(padrao.tetoDoIntervalo()).isEqualTo(Duration.ofSeconds(16));
        assertThat(padrao.tentativas()).isEqualTo(4);
        assertThat(padrao.fila()).isEqualTo("catalog.expediente-alterado");
        assertThat(padrao.filaMorta()).isEqualTo("catalog.expediente-alterado.morta");
    }

    @Test
    @DisplayName("um teto menor que o intervalo derruba a subida, em vez de ser corrigido em silêncio")
    void valor_invalido_derruba() {
        assertThatThrownBy(() -> ligar(Map.of(
                "delivery.consumo-de-eventos.intervalo", "20s",
                "delivery.consumo-de-eventos.teto-do-intervalo", "10s")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
