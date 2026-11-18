package com.deliveryplatform.merchant.application.port.in;

import com.deliveryplatform.merchant.domain.model.Modalidade;

import java.util.UUID;

/**
 * As condições que a T01 congela, para uma modalidade e um endereço (ADR-087).
 *
 * <p><b>Sem cache, em lugar nenhum</b>: a resposta é congelada no pedido (ADR-034). O bairro manda,
 * e o CEP é a alternativa — a área é escolhida, e o CEP só pré-seleciona (ADR-020). Vínculo ativo,
 * e nenhuma permissão, como o {@code expediente-corrente}.
 */
public interface ConsultarCondicoesDoPedido {

    CondicoesDoPedido de(UUID estabelecimentoId, UUID usuarioId,
                         Modalidade modalidade, String bairro, String cep);
}
