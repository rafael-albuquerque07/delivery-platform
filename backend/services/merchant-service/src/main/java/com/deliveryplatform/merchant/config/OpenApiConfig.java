package com.deliveryplatform.merchant.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dá nome ao contrato do {@code merchant} (ADR-039) — e, diferente do
 * {@code identity}, este <b>declara o esquema de segurança</b>.
 *
 * <p>Na rodada do cadastro eu tirei o {@code securitySchemes} do
 * {@code identity} porque lá nenhuma operação do contrato era protegida: login,
 * JWKS e os dois passos do cadastro são todos públicos, e o bloco entraria em
 * {@code components} sem ninguém referenciá-lo. Aqui é o oposto exato —
 * <b>nenhuma</b> operação é pública —, então o esquema entra e o requisito é
 * declarado na raiz, valendo para todas.
 *
 * <p>Declarar na raiz e não operação a operação não é economia: é a forma que
 * não erra. Um endpoint novo nasce protegido no contrato por omissão, e quem
 * quiser abri-lo tem de dizer isso explicitamente — que é a ordem certa das
 * coisas para um serviço onde o padrão é exigir vínculo.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA = "bearerAuth";

    @Bean
    public OpenAPI merchantOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("merchant-service")
                        .version("v1")
                        .description("""
                                Estabelecimento, equipe e áreas de entrega. Toda rota exige \
                                token emitido pelo identity-service, e a autorização é \
                                resolvida pelo vínculo do usuário com a loja da URL."""))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token do identity-service. Seis claims, sem papel nem permissão.")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
