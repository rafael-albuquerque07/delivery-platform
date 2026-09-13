package com.deliveryplatform.merchant.domain.model;

/**
 * Que subestados de preparo a loja libera (`estabelecimento.md` §4).
 *
 * <p>Quem valida o subestado é o {@code order-service}, consultando este valor.
 * Alterar o tipo <b>não</b> altera pedido em andamento: um pedido em produção
 * continua válido se a loja virar separação.
 */
public enum TipoDeOperacao {
    /** NA_FILA · EM_PRODUCAO · FINALIZANDO */
    PRODUCAO,

    /** SEPARANDO · CONFERIDO · EMBALADO */
    SEPARACAO,

    /** Os seis. */
    MISTA
}
