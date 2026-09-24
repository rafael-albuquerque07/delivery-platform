package com.deliveryplatform.gateway.integration;

import com.deliveryplatform.gateway.support.GatewayNoAr;
import com.deliveryplatform.gateway.support.UpstreamsDeMentira;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As quinze rotas da ADR-012, uma a uma, contra o gateway de pé.
 *
 * <p><b>O que esta classe substitui.</b> O {@code application.yml} carregava
 * quinze linhas de comentário avisando que o namespace
 * {@code spring.cloud.gateway.server.webmvc.routes} nunca tinha sido verificado,
 * que a variante reativa usa outro, e que <i>"o build ficar verde NÃO prova nada
 * aqui"</i> — terminando com uma instrução para um humano rodar {@code curl}.
 *
 * <p>Instrução para um humano é asserção sem executor. Se o prefixo estiver
 * errado, <b>nenhuma</b> rota casa: o gateway devolve 404 do próprio Tomcat em
 * vez de encaminhar, e esta classe inteira fica vermelha — em vez de o serviço
 * subir calado em produção e não rotear nada.
 *
 * <p>Cada caso confere três coisas de uma vez: que a rota casou, que casou com
 * o <b>serviço certo</b> (cada dublê responde o próprio nome, de uma porta
 * própria), e que o caminho <b>atravessou inteiro</b>, sem reescrita nem corte
 * de prefixo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoteamentoIT extends GatewayNoAr {

    private static final String LOJA = "7b1f0c22-9d3e-4a51-8f6b-2c0d7e5a1934";

    private String comToken(String caminho) {
        return cliente().get().uri(caminho)
                .header("Authorization", "Bearer " + IDENTITY.tokenDe(UUID.randomUUID()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();
    }

    @DisplayName("cada recurso chega no serviço que a ADR-012 nomeia")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "/api/v1/me/vinculos,                             identity",
            "/api/v1/merchants/{loja}/settings,               merchant",
            "/api/v1/merchants/{loja}/team,                   merchant",
            "/api/v1/merchants/{loja}/areas,                  merchant",
            "/api/v1/merchants/{loja}/couriers,               merchant",
            "/api/v1/merchants/{loja}/catalog/produtos,       catalog",
            "/api/v1/merchants/{loja}/shifts,                 settlement",
            "/api/v1/merchants/{loja}/orders,                 order",
            "/api/v1/merchants/{loja}/payments,               payment",
            "/api/v1/merchants/{loja}/deliveries,             delivery",
            "/api/v1/couriers/me/entregas,                    delivery"
    })
    void a_rota_leva_ao_servico_certo(String modelo, String servicoEsperado) {
        String caminho = modelo.trim().replace("{loja}", LOJA);

        String corpo = comToken(caminho);

        assertThat(corpo)
                .as("a rota precisa casar E casar com o dono certo — portas "
                        + "diferentes, nomes diferentes, sem empate possível")
                .contains("\"servico\":\"" + servicoEsperado + "\"")
                .contains("\"caminho\":\"" + caminho + "\"");
    }

    @DisplayName("o prefixo /auth vai ao identity, e sem token")
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/verification-code",
            "/api/v1/auth/qualquer-rota-que-ainda-nao-existe"
    })
    void o_prefixo_auth_atravessa_sem_token(String caminho) {
        String corpo = cliente().get().uri(caminho)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(corpo)
                .as("o gateway abre o prefixo inteiro; quem decide rota a rota é "
                        + "o identity, cuja cadeia é anyRequest().authenticated() "
                        + "por padrão (ADR-044 §2)")
                .contains("\"servico\":\"identity\"");
    }

    @DisplayName("cada webhook vai ao serviço que valida a assinatura dele")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "/api/v1/webhooks/psp/pix,          payment",
            "/api/v1/webhooks/channel/whatsapp, conversation"
    })
    void o_webhook_vai_ao_dono_da_assinatura(String caminho, String servicoEsperado) {
        String corpo = cliente().post().uri(caminho.trim())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(corpo).contains("\"servico\":\"" + servicoEsperado.trim() + "\"");
    }

    @Test
    void o_token_atravessa_para_o_servico() {
        String corpo = comToken("/api/v1/merchants/" + LOJA + "/team");

        assertThat(corpo)
                .as("o serviço revalida o token — é o que impede que alcançar a "
                        + "rede interna, por qualquer caminho, equivalha a estar "
                        + "autorizado (ADR-012). Um gateway que autentica e retira "
                        + "o cabeçalho transforma todo serviço num serviço que "
                        + "confia na rede.")
                .contains("\"autorizacao\":true");
    }

    @Test
    void caminho_que_nenhuma_rota_cobre_nao_vaza_para_servico_nenhum() {
        cliente().get().uri("/api/v1/inventory/skus")
                .header("Authorization", "Bearer " + IDENTITY.tokenDe(UUID.randomUUID()))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void o_upstream_do_catalog_e_o_do_merchant_nao_se_confundem() {
        // As duas rotas compartilham o prefixo /merchants/{id}/ e diferem só no
        // segmento seguinte. É onde a ordem dos predicados erra em silêncio: uma
        // rota mal ordenada captura o que era de outra e ninguém percebe até o
        // recurso errado responder.
        assertThat(comToken("/api/v1/merchants/" + LOJA + "/catalog/x"))
                .contains("\"servico\":\"" + UpstreamsDeMentira.CATALOG + "\"");
        assertThat(comToken("/api/v1/merchants/" + LOJA + "/team"))
                .contains("\"servico\":\"" + UpstreamsDeMentira.MERCHANT + "\"");
    }
}
