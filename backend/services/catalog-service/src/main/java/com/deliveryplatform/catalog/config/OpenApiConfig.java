package com.deliveryplatform.catalog.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dá nome ao contrato do {@code catalog} (ADR-039) — terceiro arquivo de
 * {@code contracts/openapi/}.
 *
 * <p>Como o do {@code merchant}, e pela mesma razão: <b>nenhuma</b> operação
 * deste serviço é pública, então o esquema de segurança entra e o requisito é
 * declarado na raiz, valendo para todas. Declarar na raiz não é economia — é a
 * forma que não erra: um endpoint novo nasce protegido no contrato por omissão,
 * e quem quiser abri-lo tem de dizer isso explicitamente.
 *
 * <p>Isso vai importar no marco 7, quando o cardápio público nascer: abrir uma
 * rota vai exigir uma linha visível no diff do contrato.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA = "bearerAuth";

    @Bean
    public OpenAPI catalogOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("catalog-service")
                        .version("v1")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT"))
                        .description("""
                                Produto, opções e disponibilidade qualitativa. Toda rota exige \
                                token emitido pelo identity-service, e a autorização é resolvida \
                                no merchant-service com o token de quem pediu, encaminhado \
                                (ADR-045)."""))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token do identity-service. Seis claims, sem papel nem "
                                + "permissão — a permissão vem do merchant, não do token.")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
