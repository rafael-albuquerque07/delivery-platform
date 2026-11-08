package com.deliveryplatform.catalog.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * O pedido de uma opção nova, dentro de um grupo que já existe (ADR-077).
 *
 * <p><b>O acréscimo aceita zero e negativo</b> ({@code catalogo.md} §4): "Sem cebola"
 * custa zero, e o desconto do cardápio é acréscimo negativo, com nome. O que o limita é
 * a C2 — nenhuma combinação válida sai por zero ou menos —, cobrada quando o produto
 * vai ao cardápio, ou já está nele.
 */
@Schema(description = "Uma opção nova no grupo. Nasce disponível.")
public record OpcaoRequest(

        @Schema(description = "O nome como o cliente o lê.", example = "Grande")
        @NotBlank(message = "nome é obrigatório")
        String nome,

        @Schema(description = "Em reais, duas casas. Zero e negativo são aceitos.",
                example = "8.00")
        @NotNull(message = "acrescimo é obrigatório")
        BigDecimal acrescimo,

        @Schema(description = "A posição da opção no grupo.", example = "0")
        @NotNull(message = "ordem é obrigatória")
        Integer ordem
) {
}
