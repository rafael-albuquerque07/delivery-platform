package com.deliveryplatform.merchant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * O {@code estabelecimentoId} volta no corpo, e é o <b>já validado</b> — o que
 * sobreviveu ao confronto com o vínculo, não o que veio na URL. Parecem o
 * mesmo valor e são coisas diferentes: um é entrada, o outro é conclusão.
 */
public record EquipeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID estabelecimentoId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MembroResponse> membros) {
}
