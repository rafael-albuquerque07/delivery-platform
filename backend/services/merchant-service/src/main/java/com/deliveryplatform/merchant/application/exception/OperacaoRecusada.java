package com.deliveryplatform.merchant.application.exception;

import java.util.Objects;

/**
 * O domínio recusou o valor da operação que o corpo descrevia — 400 (ADR-080).
 *
 * <p>No molde do {@link HorarioRecusado}: o {@code Operacao} recusa com
 * {@code IllegalArgumentException}, e a tradução nasce em <b>um</b> ponto só por rota — o caso
 * de uso, em volta da construção. A frase é do tratador; a mensagem do domínio fica na causa.
 */
public class OperacaoRecusada extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** As recusas que o {@code Operacao} tem para estes dois campos, e só elas. */
    public enum Motivo {
        /**
         * M17: modalidade aceita sem entrada, entrada para modalidade que a loja não aceita, ou
         * mínimo negativo. O construtor as recusa juntas, e a frase cobre as três.
         */
        PEDIDO_MINIMO,
        /** M15: desconto de retirada negativo. */
        DESCONTO_NEGATIVO
    }

    private final Motivo motivo;

    public OperacaoRecusada(Motivo motivo, Throwable causa) {
        super(Objects.requireNonNull(motivo, "motivo") + ": " + causa.getMessage(), causa);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
