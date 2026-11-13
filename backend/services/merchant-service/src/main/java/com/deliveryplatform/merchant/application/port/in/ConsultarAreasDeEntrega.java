package com.deliveryplatform.merchant.application.port.in;

import com.deliveryplatform.merchant.domain.model.AreaDeEntrega;

import java.util.List;
import java.util.UUID;

/**
 * As áreas de entrega da loja, ativas e desativadas, para quem as configura (ADR-081). Exige
 * {@code CONFIGURAR_LOJA}, como a escrita: quem lê é quem vai trocá-las.
 */
public interface ConsultarAreasDeEntrega {

    List<AreaDeEntrega> daLoja(UUID lojaId, UUID autorUsuarioId);
}
