package com.deliveryplatform.merchant.application.port.in;

import com.deliveryplatform.merchant.domain.model.Operacao;

import java.util.UUID;

/**
 * A operação da loja, para quem a configura — a leitura do pedido mínimo e do desconto de
 * retirada (ADR-080). Exige {@code CONFIGURAR_LOJA}, como a escrita: quem lê é quem vai trocar.
 */
public interface ConsultarOperacao {

    Operacao daLoja(UUID lojaId, UUID autorUsuarioId);
}
