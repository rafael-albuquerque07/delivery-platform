package com.deliveryplatform.identity.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dá nome ao contrato (ADR-039).
 *
 * <p>Sem este bean o springdoc escreve {@code "title": "OpenAPI definition"} e
 * {@code "version": "v0"} — e foi exatamente isso que o
 * {@code contracts/openapi/identity-service.json} guardou congelado. Num
 * diretório com nove arquivos, todos chamados "OpenAPI definition", o nome
 * deixa de ser detalhe: é o que um gerador de cliente usa para nomear pacote e
 * classe.
 *
 * <p><b>Não há {@code securitySchemes} aqui, e a ausência é decisão.</b> Este
 * serviço não expõe, hoje, nenhuma operação protegida: login, JWKS e os dois
 * passos do cadastro são todos públicos, e o {@code /v3/api-docs} — que é
 * protegido — não entra no próprio documento. Declarar o esquema {@code bearer}
 * agora poria em {@code components} um bloco que operação nenhuma referencia:
 * a mesma peça-que-nunca-rodou que este projeto já removeu nove vezes. Ele
 * nasce junto com o primeiro endpoint que o exige — o do {@code merchant}, na
 * rodada C — e lá nasce referenciado.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI identityOpenApi() {
        return new OpenAPI().info(new Info()
                .title("identity-service")
                .version("v1")
                .description("""
                        Conta de acesso ao painel: cadastro com verificação por \
                        telefone, login e a chave pública com que os demais \
                        serviços validam o token."""));
    }
}
