package com.deliveryplatform.merchant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * A bandeira da semeadura de desenvolvimento (ADR-059).
 *
 * <p><b>Falsa por padrão</b>, e é a única forma aceitável: semeadura ligada em
 * qualquer lugar que não seja a máquina de quem desenvolve é dado inventado
 * em produção. Esquecer fecha.
 */
@ConfigurationProperties(prefix = "delivery.semeadura")
public record SemeaduraProperties(@DefaultValue("false") boolean ligada) {
}
