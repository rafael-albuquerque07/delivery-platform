package com.deliveryplatform.merchant.application.port.in;

import com.deliveryplatform.merchant.domain.model.AreaDeEntrega;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Substitui as áreas de entrega da loja inteiras (ADR-020, ADR-081). Exige
 * {@code CONFIGURAR_LOJA}.
 *
 * <p>As áreas chegam cruas, e não como {@code AreaDeEntrega}: construir a faixa, a área e a
 * loja é onde o domínio pode recusar, e a recusa tem de acontecer dentro do caso de uso, que é o
 * único ponto de tradução.
 */
public interface DefinirAreasDeEntrega {

    List<AreaDeEntrega> definir(UUID lojaId, UUID autorUsuarioId, List<AreaPedida> areas);

    record AreaPedida(String nome, BigDecimal taxa, boolean ativa, List<FaixaPedida> faixasDeCep) {
    }

    record FaixaPedida(String inicio, String fim) {
    }
}
