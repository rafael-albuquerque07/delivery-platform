package com.deliveryplatform.catalog.application.port.in;

import com.deliveryplatform.catalog.domain.model.Produto;

import java.util.UUID;

/** O produto sai do cardápio — {@code ATIVO → INATIVO} (ADR-078). */
public interface DespublicarProduto {

    Produto despublicar(UUID estabelecimentoId, UUID produtoId);
}
