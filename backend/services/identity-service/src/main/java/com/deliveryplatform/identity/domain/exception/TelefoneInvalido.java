package com.deliveryplatform.identity.domain.exception;

/**
 * O telefone recebido não é E.164 válido depois da normalização de
 * {@code Telefone.de(...)} — nem no formato original, nem com o prefixo
 * brasileiro presumido pela P2.
 */
public class TelefoneInvalido extends RuntimeException {

    public TelefoneInvalido(String valorRecebido) {
        super("Telefone inválido: " + valorRecebido);
    }
}
