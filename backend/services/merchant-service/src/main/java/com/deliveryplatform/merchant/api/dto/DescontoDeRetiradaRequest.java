package com.deliveryplatform.merchant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** O desconto de retirada (ADR-024, ADR-080): um valor fixo para a loja inteira. */
public record DescontoDeRetiradaRequest(

        @Schema(description = "Em reais, abatido do pedido de quem retira no balcão. Zero é "
                + "sem desconto; negativo é recusado.", example = "5.00")
        @NotNull(message = "descontoDeRetirada é obrigatório")
        BigDecimal descontoDeRetirada
) {
}
