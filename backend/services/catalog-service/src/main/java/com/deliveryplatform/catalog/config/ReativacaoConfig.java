package com.deliveryplatform.catalog.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra o {@link ReativacaoProperties}. Este serviço registra propriedades
 * explicitamente, perto de quem as usa — como o {@code SecurityConfig} e a
 * {@code ConfiguracaoDaEscuta} —, e não por varredura.
 */
@Configuration
@EnableConfigurationProperties(ReativacaoProperties.class)
public class ReativacaoConfig {
}
