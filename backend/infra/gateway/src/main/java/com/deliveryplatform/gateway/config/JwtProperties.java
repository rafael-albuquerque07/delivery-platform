package com.deliveryplatform.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Emissor e audiência que o token precisa declarar.
 *
 * <p>Gêmeo do {@code JwtProperties} do {@code merchant-service}, e a duplicação
 * é deliberada: a ADR-001 proíbe o gateway de depender de um serviço, e um
 * módulo compartilhado de segurança seria um segundo {@code :value-types} —
 * decisão maior do que a rodada que escreveu este arquivo. Fica registrado na
 * ADR-044 com gatilho: o terceiro módulo que precisar do mesmo decoder.
 *
 * <p><b>Não tem chave privada</b>, e nunca vai ter. O gateway valida; quem
 * assina é o {@code identity-service}.
 */
@ConfigurationProperties(prefix = "delivery.jwt")
public record JwtProperties(String issuer, String audience) {
}
