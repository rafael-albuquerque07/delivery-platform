package com.deliveryplatform.merchant.application.port.in;

import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Operacao;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Substitui os pedidos mínimos da loja, por modalidade (ADR-028, ADR-080). Exige
 * {@code CONFIGURAR_LOJA}.
 *
 * <p>Os valores chegam crus: construir a operação é onde o domínio pode recusar, e a recusa
 * tem de acontecer dentro do caso de uso, que é o único ponto de tradução.
 */
public interface DefinirPedidoMinimo {

    Operacao definir(UUID lojaId, UUID autorUsuarioId, Map<Modalidade, BigDecimal> minimos);
}
