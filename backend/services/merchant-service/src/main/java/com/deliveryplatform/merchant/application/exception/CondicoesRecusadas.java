package com.deliveryplatform.merchant.application.exception;

import java.util.Objects;

/**
 * O pedido de condições não tem como ser respondido — 400 (ADR-087).
 *
 * <p>No molde do {@link AreasRecusadas}: a frase é do tratador, por motivo; a mensagem do domínio,
 * quando há, fica na causa.
 */
public class CondicoesRecusadas extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Motivo {
        /** Entrega sem bairro e sem CEP: não há área a procurar. */
        ENDERECO_AUSENTE,
        /** O CEP não tem oito dígitos — o {@code FaixaDeCep} o recusa. */
        CEP_INVALIDO
    }

    private final Motivo motivo;

    public CondicoesRecusadas(Motivo motivo) {
        super(Objects.requireNonNull(motivo, "motivo").name());
        this.motivo = motivo;
    }

    public CondicoesRecusadas(Motivo motivo, Throwable causa) {
        super(Objects.requireNonNull(motivo, "motivo") + ": " + causa.getMessage(), causa);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
