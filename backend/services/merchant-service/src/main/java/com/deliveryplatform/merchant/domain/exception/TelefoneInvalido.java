package com.deliveryplatform.merchant.domain.exception;

/**
 * O telefone recebido não é E.164 válido depois da normalização de
 * {@code Telefone.de(...)} — nem no formato original, nem com o prefixo
 * brasileiro presumido pela P2.
 *
 * <p>Gêmeo do {@code TelefoneInvalido} do {@code identity-service}, e a
 * duplicação é a que a ADR-040 decidiu: os dois {@code Telefone} são tipos
 * diferentes com a mesma sintaxe, e compartilhar a exceção compartilharia a
 * impressão de que são a mesma coisa.
 */
public class TelefoneInvalido extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TelefoneInvalido(String valorRecebido) {
        super("Telefone inválido: " + valorRecebido);
    }
}
