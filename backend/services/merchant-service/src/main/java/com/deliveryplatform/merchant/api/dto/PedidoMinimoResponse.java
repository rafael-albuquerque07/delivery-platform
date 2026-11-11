package com.deliveryplatform.merchant.api.dto;

import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Operacao;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/** Os pedidos mínimos como estão gravados: uma entrada por modalidade aceita, zero inclusive. */
public record PedidoMinimoResponse(Map<Modalidade, BigDecimal> pedidoMinimoPorModalidade) {

    public static PedidoMinimoResponse de(Operacao operacao) {
        Map<Modalidade, BigDecimal> porModalidade = new EnumMap<>(Modalidade.class);
        operacao.pedidoMinimoPorModalidade()
                .forEach((modalidade, minimo) -> porModalidade.put(modalidade, minimo.valor()));
        return new PedidoMinimoResponse(porModalidade);
    }
}
