package com.deliveryplatform.merchant.api.dto;

import com.deliveryplatform.merchant.domain.model.Operacao;

import java.math.BigDecimal;

/** O desconto de retirada como está gravado. */
public record DescontoDeRetiradaResponse(BigDecimal descontoDeRetirada) {

    public static DescontoDeRetiradaResponse de(Operacao operacao) {
        return new DescontoDeRetiradaResponse(operacao.descontoDeRetirada().valor());
    }
}
