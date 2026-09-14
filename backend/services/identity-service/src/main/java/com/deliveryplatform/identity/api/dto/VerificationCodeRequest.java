package com.deliveryplatform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Superfície HTTP em inglês, identificador de negócio em português (ADR-035),
 * como no {@code LoginRequest}.
 *
 * <p>Só presença. O formato do telefone é validado no caso de uso, pelo
 * {@code Telefone.de} — que é o único lugar do serviço que sabe presumir o
 * {@code +55} da P2, e duplicar essa regra numa anotação seria criar uma
 * segunda definição de "telefone válido".
 */
public record VerificationCodeRequest(@NotBlank String telefone) {
}
