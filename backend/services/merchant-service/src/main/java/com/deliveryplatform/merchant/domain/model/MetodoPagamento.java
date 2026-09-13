package com.deliveryplatform.merchant.domain.model;

/**
 * O que a loja aceita receber. É o <b>declarado</b> — o método efetivamente
 * liquidado é outro campo, em outro serviço, e a ADR-009 diz que os dois
 * diferirem é o caso normal.
 *
 * <p>Sem {@code NAO_LIQUIDADO} aqui: aquilo é desfecho de liquidação, não
 * configuração de loja. Ninguém aceita "não pagar" como método.
 */
public enum MetodoPagamento {
    DINHEIRO,
    CARTAO,
    PIX
}
