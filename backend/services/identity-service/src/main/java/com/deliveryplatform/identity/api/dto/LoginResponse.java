package com.deliveryplatform.identity.api.dto;

/**
 * Não devolve o {@code Usuario}. O token já carrega o {@code sub}, e nome ou
 * e-mail no corpo do login espalhariam dado pessoal por mais um lugar (ADR-013).
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {
}
