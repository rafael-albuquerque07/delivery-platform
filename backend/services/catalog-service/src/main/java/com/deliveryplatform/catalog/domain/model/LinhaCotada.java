package com.deliveryplatform.catalog.domain.model;

import com.deliveryplatform.valuetypes.Money;
import java.util.List;
import java.util.UUID;

/**
 * Um item do pedido, cotado: o que a T01 vai congelar (ADR-018, ADR-088).
 *
 * <p>Os nomes vão junto do preço porque é isso que se congela — o produto e a opção podem mudar de
 * nome amanhã, e o pedido de hoje continua dizendo o que foi vendido.
 *
 * @param opcoes        na ordem dos grupos e, dentro do grupo, na ordem das opções
 * @param precoUnitario {@code precoBase + Σ acrescimo}
 * @param subtotal      {@code precoUnitario × quantidade}
 */
public record LinhaCotada(
        UUID produtoId,
        String nomeDoProduto,
        Money precoBase,
        List<OpcaoCotada> opcoes,
        Money precoUnitario,
        int quantidade,
        Money subtotal,
        ModoDeControle modoDeControle
) {

    public LinhaCotada {
        opcoes = List.copyOf(opcoes);
    }
}
