package com.deliveryplatform.identity.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * O segundo canal (ADR-029 §1) — pedido, não exigido. Guardado em caixa
 * canônica (minúsculas) para que a unicidade de U2 não dependa de como a
 * pessoa digitou o próprio endereço.
 */
public record Email(String endereco) {

    private static final Pattern FORMATO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        String normalizado = endereco == null ? null : endereco.trim().toLowerCase(Locale.ROOT);
        if (normalizado == null || normalizado.isEmpty() || !FORMATO.matcher(normalizado).matches()) {
            throw new IllegalArgumentException("E-mail inválido: " + endereco);
        }
        endereco = normalizado;
    }
}
