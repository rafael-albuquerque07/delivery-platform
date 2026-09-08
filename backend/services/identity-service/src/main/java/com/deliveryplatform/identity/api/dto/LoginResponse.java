package com.deliveryplatform.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Não devolve o {@code Usuario}. O token já carrega o {@code sub}, e nome ou
 * e-mail no corpo do login espalhariam dado pessoal por mais um lugar (ADR-013).
 *
 * <p>Os três são obrigatórios e o contrato precisa dizer isso. O
 * {@code LoginRequest} ganha {@code required} de graça pelas anotações de
 * validação; a resposta não é validada, então sem isto um cliente gerado
 * trataria o {@code accessToken} como opcional -- num endpoint cuja resposta
 * é o token.
 */
public record LoginResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tokenType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expiresIn) {
}
