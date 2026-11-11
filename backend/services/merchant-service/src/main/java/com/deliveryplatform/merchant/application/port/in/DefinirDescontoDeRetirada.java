package com.deliveryplatform.merchant.application.port.in;

import com.deliveryplatform.merchant.domain.model.Operacao;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Troca o desconto de retirada da loja (ADR-024, ADR-080). Exige {@code CONFIGURAR_LOJA}.
 */
public interface DefinirDescontoDeRetirada {

    Operacao definir(UUID lojaId, UUID autorUsuarioId, BigDecimal desconto);
}
