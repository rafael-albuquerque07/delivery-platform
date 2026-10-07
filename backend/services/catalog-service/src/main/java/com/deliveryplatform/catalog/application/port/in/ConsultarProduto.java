package com.deliveryplatform.catalog.application.port.in;

import com.deliveryplatform.catalog.domain.model.Produto;
import java.util.UUID;

/**
 * Um produto da loja, inteiro — com os grupos e as opções.
 *
 * <p>Devolve o <b>agregado</b>, e não o DTO: quem mapeia é a borda, como o
 * {@code ListarProdutos} já faz. O caso de uso não sabe que existe HTTP.
 */
public interface ConsultarProduto {

    /**
     * @param estabelecimentoId a loja <b>da URL</b>, e ela é o que autoriza
     * @param produtoId o produto pedido
     * @throws com.deliveryplatform.catalog.application.exception.ProdutoNaoEncontrado
     *         se não existir <b>ou for de outra loja</b> — a mesma resposta para os
     *         dois casos, de propósito
     */
    Produto consultar(UUID estabelecimentoId, UUID produtoId);
}
