package com.deliveryplatform.identity.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * As quatro propriedades que a ADR-037 decidiu. Primeira classe de propriedades
 * do projeto — os outros oito serviços vão copiar esta forma.
 *
 * <p>A validação está no construtor canônico, e não em anotações de Bean
 * Validation, por dois motivos: não acrescenta dependência, e o
 * {@code Telefone} já estabeleceu que value object recusa estado inválido no
 * próprio construtor. Propriedade ausente derruba a subida do contexto — que é o
 * comportamento certo para uma chave de assinatura.
 */
@ConfigurationProperties(prefix = "delivery.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        String privateKeyPath) {

    public JwtProperties {
        exigir(issuer, "delivery.jwt.issuer");
        exigir(audience, "delivery.jwt.audience");
        exigir(privateKeyPath, "delivery.jwt.private-key-path");

        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalStateException(
                    "delivery.jwt.access-token-ttl precisa ser uma duração positiva — "
                            + "a ADR-037 decidiu PT30M, e o valor mora no yml, não no código");
        }
    }

    private static void exigir(String valor, String nome) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                    nome + " não está definida. A ADR-037 exige as quatro propriedades; "
                            + "sem chave privada não existe token, e subir sem ela seria "
                            + "descobrir isso na primeira requisição");
        }
    }
}
