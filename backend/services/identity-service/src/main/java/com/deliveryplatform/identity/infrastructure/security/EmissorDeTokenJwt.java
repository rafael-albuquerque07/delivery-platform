package com.deliveryplatform.identity.infrastructure.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.deliveryplatform.identity.application.port.out.EmissorDeToken;
import com.deliveryplatform.identity.application.port.out.TokenEmitido;
import com.deliveryplatform.identity.config.JwtProperties;

/**
 * Emite com {@code NimbusJwtEncoder} (ADR-015), com os <b>seis</b> claims da
 * emenda de 26/08 e nada mais.
 *
 * <p>Não há {@code roles} nem {@code scope}: papel fica velho e é por
 * estabelecimento, e um {@code scope} que nada lê é permissão esperando para
 * voltar ao token sem ninguém decidir isso. A autorização é resolvida por
 * requisição contra o {@code merchant-service} (ADR-011).
 *
 * <p>O {@code kid} não é escrito aqui: o encoder o preenche a partir da chave
 * que o {@code JWKSource} seleciona. Escrevê-lo à mão seria uma segunda fonte
 * para o mesmo dado, e a rotação é onde as duas divergiriam.
 */
@Component
public class EmissorDeTokenJwt implements EmissorDeToken {

    private final JwtEncoder encoder;
    private final JwtProperties propriedades;

    public EmissorDeTokenJwt(JwtEncoder encoder, JwtProperties propriedades) {
        this.encoder = encoder;
        this.propriedades = propriedades;
    }

    @Override
    public TokenEmitido emitir(UUID usuarioId) {
        Instant agora = Instant.now();
        Instant expira = agora.plus(propriedades.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propriedades.issuer())
                .subject(usuarioId.toString())
                .audience(List.of(propriedades.audience()))
                .issuedAt(agora)
                .expiresAt(expira)
                .id(UUID.randomUUID().toString())
                .build();

        String valor = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        return new TokenEmitido(valor, agora, expira);
    }
}
