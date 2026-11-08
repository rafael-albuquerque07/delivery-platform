package com.deliveryplatform.catalog.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * O pedido de um grupo de opções novo (ADR-077). <b>Só forma aqui</b>: presente e não
 * branco. A C3 — {@code 0 ≤ minEscolhas ≤ maxEscolhas}, e {@code maxEscolhas ≥ 1} — é
 * do {@code GrupoDeOpcoes}, e chega como 400 pela tradução do agregado (ADR-063).
 *
 * <p><b>Sem opções</b>: o grupo nasce vazio, e cada opção entra pela rota dela.
 */
@Schema(description = "Um grupo de opções novo, vazio. As opções entram uma a uma.")
public record GrupoRequest(

        @Schema(description = "A pergunta, como o cliente a lê.", example = "Tamanho")
        @NotBlank(message = "nome é obrigatório")
        String nome,

        @Schema(description = "Quantas o cliente tem de escolher. Zero é grupo opcional.",
                example = "1")
        @NotNull(message = "minEscolhas é obrigatório")
        Integer minEscolhas,

        @Schema(description = "Quantas o cliente pode escolher, no máximo. Pelo menos 1, "
                + "e nunca menor que o mínimo.", example = "1")
        @NotNull(message = "maxEscolhas é obrigatório")
        Integer maxEscolhas,

        @Schema(description = "A posição do grupo no produto.", example = "0")
        @NotNull(message = "ordem é obrigatória")
        Integer ordem
) {
}
