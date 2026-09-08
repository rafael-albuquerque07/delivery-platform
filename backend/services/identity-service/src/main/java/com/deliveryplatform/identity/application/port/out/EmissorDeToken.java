package com.deliveryplatform.identity.application.port.out;

import java.util.UUID;

/**
 * Emite o access token de um usuário já autenticado.
 *
 * <p>Recebe o {@code id} e nada mais, e isso é a ADR-015 emendada em forma de
 * assinatura: o {@code sub} é o único dado de identidade que o token carrega.
 * Uma porta que recebesse o {@code Usuario} inteiro convidaria, na primeira
 * pressa, a pôr o nome ou o telefone dentro do token.
 */
public interface EmissorDeToken {

    TokenEmitido emitir(UUID usuarioId);
}
