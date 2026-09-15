package com.deliveryplatform.merchant.support;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Um {@code identity-service} de mentira: gera um par RSA, <b>publica o JWKS
 * num servidor HTTP de verdade</b> e assina tokens com a chave privada.
 *
 * <p><b>Por que servir o JWKS em vez de injetar um {@code JwtDecoder} de
 * teste.</b> Um decoder dublê provaria que o controlador funciona e não provaria
 * nada sobre o caminho que roda em produção: buscar a chave pela rede, escolher
 * pelo {@code kid}, validar assinatura, emissor e audiência. É o mesmo
 * argumento que o {@code GeradorDeChaveDeTeste} do {@code identity} usa para
 * escrever um PEM em disco em vez de usar um par em memória — <i>o caminho de
 * produção passa a ser o caminho de teste</i>. Sem isto, a primeira vez que o
 * {@code merchant} buscaria um JWKS seria em produção.
 *
 * <p>O servidor é o {@link HttpServer} do próprio JDK: nenhuma dependência
 * nova, e ele morre com o teste.
 *
 * <p><b>Ele também sabe assinar token errado</b> — emissor de outro ambiente,
 * audiência de outro produto, já expirado, {@code sub} que não é UUID. Cada um
 * desses é um teste, e nenhum deles existiria se a chave fosse um dublê que
 * aceita tudo.
 */
public final class IdentityDeMentira implements AutoCloseable {

    public static final String EMISSOR = "http://identity-de-mentira";
    public static final String AUDIENCIA = "delivery-platform";

    private final RSAKey chave;
    private final HttpServer servidor;

    private IdentityDeMentira(RSAKey chave, HttpServer servidor) {
        this.chave = chave;
        this.servidor = servidor;
    }

    public static IdentityDeMentira subir() {
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(2048);
            KeyPair par = gerador.generateKeyPair();

            RSAKey chave = new RSAKey.Builder((RSAPublicKey) par.getPublic())
                    .privateKey((RSAPrivateKey) par.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();

            byte[] jwks = new JWKSet(chave.toPublicJWK())
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);

            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/.well-known/jwks.json", troca -> {
                troca.getResponseHeaders().add("Content-Type", "application/json");
                troca.sendResponseHeaders(200, jwks.length);
                try (var saida = troca.getResponseBody()) {
                    saida.write(jwks);
                }
            });
            servidor.start();

            return new IdentityDeMentira(chave, servidor);
        } catch (Exception e) {
            throw new IllegalStateException("não subiu o identity de mentira", e);
        }
    }

    public String jwksUri() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/.well-known/jwks.json";
    }

    /** O token do caminho feliz: os seis claims da ADR-015 e nada mais. */
    public String tokenDe(UUID usuarioId) {
        return assinar(EMISSOR, AUDIENCIA, usuarioId.toString(), Instant.now().plus(Duration.ofMinutes(30)));
    }

    public String tokenComEmissor(String emissor, UUID usuarioId) {
        return assinar(emissor, AUDIENCIA, usuarioId.toString(), Instant.now().plus(Duration.ofMinutes(30)));
    }

    public String tokenComAudiencia(String audiencia, UUID usuarioId) {
        return assinar(EMISSOR, audiencia, usuarioId.toString(), Instant.now().plus(Duration.ofMinutes(30)));
    }

    public String tokenExpirado(UUID usuarioId) {
        return assinar(EMISSOR, AUDIENCIA, usuarioId.toString(), Instant.now().minus(Duration.ofMinutes(1)));
    }

    public String tokenComSujeito(String sujeito) {
        return assinar(EMISSOR, AUDIENCIA, sujeito, Instant.now().plus(Duration.ofMinutes(30)));
    }

    private String assinar(String emissor, String audiencia, String sujeito, Instant expira) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(emissor)
                    .subject(sujeito)
                    .audience(List.of(audiencia))
                    .issueTime(Date.from(Instant.now().minusSeconds(5)))
                    .expirationTime(Date.from(expira))
                    .jwtID(UUID.randomUUID().toString())
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(chave.getKeyID())
                            .build(),
                    claims);

            jwt.sign(new RSASSASigner(chave.toRSAPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("não assinou o token de teste", e);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
