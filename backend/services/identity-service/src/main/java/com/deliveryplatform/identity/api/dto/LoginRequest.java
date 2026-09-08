package com.deliveryplatform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Superfície HTTP em inglês, identificador de negócio em português (ADR-035).
 * {@code telefone} é palavra que a Marli usa; {@code login} e {@code auth} são
 * vocabulário de engenharia e ficam no caminho da rota.
 *
 * <p>A validação aqui é só de presença. <b>Formato de telefone não se valida na
 * borda</b>: um 400 para número malformado e um 401 para número válido e
 * inexistente seriam duas respostas diferentes, e a diferença entre elas é
 * informação sobre o cadastro.
 */
public record LoginRequest(
        @NotBlank String telefone,
        @NotBlank String senha) {
}
