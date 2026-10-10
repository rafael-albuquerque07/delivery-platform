package com.deliveryplatform.catalog.api.dto;

import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.Produto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * O produto como a lista o mostra — um resumo, e não a árvore inteira.
 *
 * <h2>O que está aqui, e o que ficou de fora</h2>
 *
 * <p>Não vêm os grupos nem as opções. Uma listagem de vinte produtos com a
 * árvore completa de cada um é um corpo de centenas de kilobytes para uma tela
 * que mostra nome, preço e um sinal de disponibilidade. Quem precisar da árvore
 * pede o produto — e essa rota nasce quando alguém precisar dela.
 *
 * <p><b>Mas o {@code vendavel} vem</b>, e ele é a razão de o resumo não ser só
 * nome e preço. Ele é derivado (§4) e o cliente <b>não consegue recalculá-lo</b>
 * sem os grupos: um produto {@code DISPONIVEL} cujo grupo obrigatório "Tamanho"
 * está inteiro esgotado não é vendável, e nada no resumo diria isso. Omiti-lo
 * obrigaria a tela a mostrar como disponível o que não dá para vender.
 *
 * <p>O {@code precoBase} sai como {@code BigDecimal}, que o Jackson serializa
 * como número JSON sem casas perdidas. <b>Não é {@code double}</b> — a ADR-009
 * existe para isso — e não é o texto do documento: aquele formato é decisão de
 * armazenamento (emenda de 27/09 à ADR-009), não de contrato HTTP.
 */
public record ProdutoResumoResponse(
        UUID id,
        UUID categoriaId,
        String nome,
        BigDecimal precoBase,
        int ordem,
        EstadoDeDisponibilidade disponibilidade,
        boolean vendavel
) {

    public static ProdutoResumoResponse de(Produto produto) {
        return new ProdutoResumoResponse(
                produto.getId(),
                produto.getCategoriaId(),
                produto.getNome(),
                produto.getPrecoBase().valor(),
                produto.getOrdem(),
                produto.getDisponibilidade().estado(),
                produto.vendavel());
    }
}
