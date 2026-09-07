package com.deliveryplatform.identity.infrastructure.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.deliveryplatform.identity.config.JwtProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * O par RSA que assina os tokens (ADR-037 §2).
 *
 * <p><b>Só a chave privada vem de arquivo.</b> A pública é derivada dela: uma
 * chave RSA em PKCS#8 no formato CRT carrega o módulo e o expoente público
 * dentro. Dois arquivos seriam duas fontes da mesma verdade, e a segunda só
 * serviria para divergir da primeira.
 *
 * <p><b>O {@code kid} é o thumbprint do RFC 7638</b>, derivado da própria chave
 * pública. Identificador escolhido à mão descola do material que nomeia, e a
 * rotação é exatamente o momento em que esse descolamento causa dano.
 */
@Component
public class ChaveDeAssinatura {

    private final RSAKey chave;

    public ChaveDeAssinatura(JwtProperties propriedades) {
        this.chave = carregar(Path.of(propriedades.privateKeyPath()));
    }

    /** O par completo — privada inclusa. Só o {@code JWKSource} deve recebê-lo. */
    public RSAKey par() {
        return chave;
    }

    /** Só a metade pública, que é o que o JWKS publica. */
    public JWKSet jwkSetPublico() {
        return new JWKSet(chave.toPublicJWK());
    }

    private static RSAKey carregar(Path caminho) {
        if (!Files.isReadable(caminho)) {
            throw new ChaveDeAssinaturaIndisponivel(
                    "chave privada ilegível ou inexistente em " + caminho
                            + " — defina delivery.jwt.private-key-path para um PKCS#8 PEM");
        }

        try {
            RSAPrivateCrtKey privada = lerPkcs8(caminho);
            RSAPublicKey publica = derivarPublica(privada);

            return new RSAKey.Builder(publica)
                    .privateKey(privada)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();

        } catch (ChaveDeAssinaturaIndisponivel e) {
            throw e;
        } catch (Exception e) {
            throw new ChaveDeAssinaturaIndisponivel(
                    "não foi possível ler a chave privada em " + caminho, e);
        }
    }

    private static RSAPrivateCrtKey lerPkcs8(Path caminho) throws Exception {
        String pem = Files.readString(caminho);
        String base64 = pem
                .replaceAll("-----BEGIN [^-]*-----", "")
                .replaceAll("-----END [^-]*-----", "")
                .replaceAll("\\s", "");

        byte[] der = Base64.getDecoder().decode(base64);
        PrivateKey lida = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));

        if (!(lida instanceof RSAPrivateCrtKey crt)) {
            throw new ChaveDeAssinaturaIndisponivel(
                    "a chave em " + caminho + " não está no formato CRT. Sem os fatores"
                            + " primos não há como derivar a pública, e o JWKS ficaria vazio");
        }
        return crt;
    }

    private static RSAPublicKey derivarPublica(RSAPrivateCrtKey privada) throws Exception {
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(
                        privada.getModulus(), privada.getPublicExponent()));
    }
}
