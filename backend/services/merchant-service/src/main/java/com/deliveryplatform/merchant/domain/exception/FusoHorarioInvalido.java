package com.deliveryplatform.merchant.domain.exception;

public class FusoHorarioInvalido extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FusoHorarioInvalido(String identificador) {
        super("fuso horário inválido ou fora do conjunto brasileiro: " + identificador);
    }
}
