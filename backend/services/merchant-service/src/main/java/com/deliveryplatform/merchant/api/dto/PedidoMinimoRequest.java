package com.deliveryplatform.merchant.api.dto;

import com.deliveryplatform.merchant.domain.model.Modalidade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Os pedidos mínimos inteiros, que substituem os que havia (ADR-028, ADR-080). A chave é a
 * modalidade — {@code ENTREGA}, {@code RETIRADA} —, e modalidade desconhecida não chega ao
 * domínio: falha na leitura do corpo, com 400.
 */
public record PedidoMinimoRequest(

        @Schema(description = "Por modalidade, o mínimo sobre o valor dos itens, sem a taxa de "
                + "entrega, em reais. Uma entrada para cada modalidade que a loja aceita, e para "
                + "nenhuma outra. Zero vale como \"sem mínimo\".")
        @NotNull(message = "pedidoMinimoPorModalidade é obrigatório")
        Map<Modalidade, @NotNull BigDecimal> pedidoMinimoPorModalidade
) {
}
