package com.deliveryplatform.merchant.api.dto;

import com.deliveryplatform.merchant.domain.model.Modalidade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * O pedido das condições (ADR-087). No corpo, e não na URL, para que o endereço não fique em log
 * de acesso.
 */
@Schema(description = "A modalidade, e o endereço quando for entrega: o bairro, ou o CEP.")
public record CondicoesDoPedidoRequest(
        @Schema(description = "A modalidade do pedido.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Modalidade modalidade,
        @Schema(description = "O bairro, como o cliente escolheu. Na entrega, ele manda.", example = "Boa Viagem")
        @Size(max = 200)
        String bairro,
        @Schema(description = "O CEP, usado quando não há bairro.", example = "51020-000")
        @Size(max = 20)
        String cep
) {
}
