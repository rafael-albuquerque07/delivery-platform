package com.deliveryplatform.merchant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * As áreas de entrega inteiras, que substituem as que havia (ADR-020, ADR-081). Lista vazia é
 * aceita, e é o estado de nascença da loja: ela não entrega em lugar nenhum.
 */
public record AreasDeEntregaRequest(

        @Schema(description = "As áreas da loja, ativas e desativadas. Lista vazia: a loja não "
                + "entrega em lugar nenhum. Duas áreas com o mesmo nome — sem distinguir caixa, "
                + "acento e espaço — são recusadas, e faixas de CEP não se cruzam entre áreas ativas.")
        @NotNull(message = "areasDeEntrega é obrigatório")
        List<@NotNull @Valid AreaRequest> areasDeEntrega
) {

    public record AreaRequest(

            @Schema(description = "O bairro, como o cliente o lê.", example = "Boa Viagem")
            @NotBlank(message = "nome da área é obrigatório")
            @Size(max = 80, message = "nome da área tem no máximo 80 caracteres")
            String nome,

            @Schema(description = "Em reais. Zero é entrega grátis nesta área — e é diferente de "
                    + "não haver área (M11).", example = "7.00")
            @NotNull(message = "taxa é obrigatória")
            BigDecimal taxa,

            @Schema(description = "Desativada continua na lista e não cota nada.")
            @NotNull(message = "ativa é obrigatório")
            Boolean ativa,

            @Schema(description = "Opcionais na regra, obrigatórias no corpo: lista vazia é a área "
                    + "alcançável só pelo nome.")
            @NotNull(message = "faixasDeCep é obrigatório")
            List<@NotNull @Valid FaixaDeCepRequest> faixasDeCep
    ) {
    }

    public record FaixaDeCepRequest(

            @Schema(description = "Oito dígitos; o hífen é aceito.", example = "51020-000")
            @NotNull(message = "inicio é obrigatório")
            String inicio,

            @Schema(description = "Oito dígitos, igual ou depois do início.", example = "51130-999")
            @NotNull(message = "fim é obrigatório")
            String fim
    ) {
    }
}
