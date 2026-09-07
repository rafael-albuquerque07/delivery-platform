package com.deliveryplatform.identity.api.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Publica a chave pública. Rota pública por decisão da ADR-037 §1: os outros
 * oito serviços precisam dela para validar token, e um JWKS protegido por token
 * é um sistema que não consegue dar a partida.
 *
 * <p><b>Este controller depende do {@code JWKSource}, e não da
 * {@code ChaveDeAssinatura}.</b> Não é preferência: o
 * {@code HexagonalArchitectureTest} proíbe {@code api} de acessar
 * {@code infrastructure}, e é a primeira vez que essa regra morde. O
 * {@code JWKSource} é tipo do Nimbus, não do nosso pacote de infraestrutura.
 *
 * <p>A projeção para {@link JWK#toPublicJWK()} é <b>explícita</b>. O
 * {@code JWKSet.toJSONObject()} já omite material privado por padrão, mas
 * confiar num padrão para não vazar chave privada é apostar que ninguém troca o
 * padrão.
 */
@RestController
public class JwksController {

    private final JWKSource<SecurityContext> fonte;

    public JwksController(JWKSource<SecurityContext> fonte) {
        this.fonte = fonte;
    }

    @GetMapping(path = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() throws Exception {
        List<JWK> publicas = fonte
                .get(new JWKSelector(new JWKMatcher.Builder().build()), null)
                .stream()
                .map(JWK::toPublicJWK)
                .toList();

        return new JWKSet(publicas).toJSONObject();
    }
}
