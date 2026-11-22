package com.deliveryplatform.catalog.api.dto;

import com.deliveryplatform.catalog.application.port.in.CotarItens.ItemACotar;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Os itens a cotar. <b>Sem preço e sem loja</b>: o preço vem do catálogo (invariante 2), e a loja
 * vem da URL (ADR-088, Decisão 1).
 */
public record CotacaoRequest(@NotEmpty List<@Valid @NotNull Item> itens) {

    public record Item(
            @NotNull UUID produtoId,
            @Min(1) int quantidade,
            List<@NotNull UUID> opcoes) {

        ItemACotar paraComando() {
            return new ItemACotar(produtoId, quantidade, opcoes);
        }
    }

    public List<ItemACotar> paraComando() {
        return itens.stream().map(Item::paraComando).toList();
    }
}
