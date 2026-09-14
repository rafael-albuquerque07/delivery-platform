package com.deliveryplatform.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Só o identificador. Nome e telefone voltariam dado pessoal para o cliente que
 * acabou de enviá-lo — mais um lugar por onde ele passa, contra a ADR-013 — e
 * token não volta daqui, porque quem emite token é o login (ADR-037).
 *
 * <p>O {@code required} é explícito pelo mesmo motivo do {@code LoginResponse}:
 * resposta não é validada, e sem isto um cliente gerado trataria como opcional
 * o único campo que este corpo tem.
 */
public record SignupResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id) {
}
