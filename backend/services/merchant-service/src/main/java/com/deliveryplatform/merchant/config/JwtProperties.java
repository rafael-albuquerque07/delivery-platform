package com.deliveryplatform.merchant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * O que este serviço exige de um token — e note o que <b>não</b> está aqui:
 * caminho de chave privada.
 *
 * <p>O {@code merchant} valida; quem emite é o {@code identity} (ADR-037). A
 * chave pública vem do JWKS pela rede, e o segredo nunca passa por aqui.
 *
 * <p><b>Emissor e audiência não são decoração.</b> O padrão do Resource Server
 * valida <b>só tempo</b>: um token expirado é recusado, e um token perfeitamente
 * válido emitido por outro sistema, não. Sem estes dois campos, {@code iss} e
 * {@code aud} seriam claims com aparência de controle e nenhum leitor — o mesmo
 * argumento com que a emenda da ADR-015 apagou o {@code scope}. O
 * {@code identity} já os valida para si desde a ADR-037; este serviço passa a
 * fazer o mesmo.
 */
@ConfigurationProperties(prefix = "delivery.jwt")
public record JwtProperties(String issuer, String audience) {
}
