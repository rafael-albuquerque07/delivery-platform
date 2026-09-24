package com.deliveryplatform.gateway.integration;

import com.deliveryplatform.gateway.support.GatewayNoAr;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A cadeia de filtros: o que passa sem token, o que exige, e o que o token
 * precisa declarar.
 *
 * <p><b>O que estava errado antes desta rodada não era a falta de segurança.</b>
 * Com o {@code starter-oauth2-resource-server} no classpath e o
 * {@code jwk-set-uri} configurado, e sem cadeia declarada, o Spring Boot
 * registra a dele: {@code anyRequest().authenticated()}. O gateway recusaria o
 * login e recusaria os webhooks do PSP — a primeira das duas direções de erro
 * que a ADR-012 nomeia, <i>"o filtro bloqueia o webhook e as notificações somem
 * em silêncio"</i>. Os dois primeiros testes aqui falhariam contra o estado
 * anterior deste módulo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CadeiaDeFiltrosIT extends GatewayNoAr {

    private static final String EQUIPE =
            "/api/v1/merchants/7b1f0c22-9d3e-4a51-8f6b-2c0d7e5a1934/team";

    // ── o que passa sem token ───────────────────────────────────────────────

    @Test
    void o_login_passa_sem_token() {
        // Exigir token para emitir token não fecha.
        cliente().post().uri("/api/v1/auth/login").exchange().expectStatus().isOk();
    }

    @Test
    void o_webhook_do_psp_passa_sem_token() {
        // O PSP não tem token nosso: a autenticação dele é assinatura no corpo,
        // conferida dentro do payment-service.
        cliente().post().uri("/api/v1/webhooks/psp/pix").exchange().expectStatus().isOk();
    }

    @Test
    void o_health_passa_sem_token() {
        cliente().get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    // ── o que não passa ─────────────────────────────────────────────────────

    @Test
    void recurso_de_loja_sem_token_e_401() {
        cliente().get().uri(EQUIPE).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void a_visao_do_entregador_sem_token_e_401() {
        cliente().get().uri("/api/v1/couriers/me/entregas")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void o_actuator_do_gateway_exige_token() {
        // Ele lista as URIs internas dos oito serviços. É diagnóstico, não
        // informação pública — e sai da lista de exposição antes de qualquer
        // ambiente exposto (ADR-044 §7).
        cliente().get().uri("/actuator/gateway/routes")
                .exchange().expectStatus().isUnauthorized();
    }

    // ── o que o token precisa declarar ──────────────────────────────────────

    @Test
    void token_de_outro_emissor_e_401() {
        // Assinado pela chave certa, com o sub certo, e de outro ambiente. O
        // decoder padrão do Resource Server valida só assinatura e tempo, e
        // deixaria este passar — era esta a falha aberta no gateway.
        pedirEquipeCom(IDENTITY.tokenComEmissor("http://homologacao", UUID.randomUUID()))
                .expectStatus().isUnauthorized();
    }

    @Test
    void token_com_audiencia_de_outro_produto_e_401() {
        pedirEquipeCom(IDENTITY.tokenComAudiencia("outro-produto", UUID.randomUUID()))
                .expectStatus().isUnauthorized();
    }

    @Test
    void token_expirado_e_401() {
        pedirEquipeCom(IDENTITY.tokenExpirado(UUID.randomUUID()))
                .expectStatus().isUnauthorized();
    }

    @Test
    void token_com_assinatura_adulterada_e_401() {
        String token = IDENTITY.tokenDe(UUID.randomUUID());
        pedirEquipeCom(token.substring(0, token.length() - 4) + "AAAA")
                .expectStatus().isUnauthorized();
    }

    @Test
    void token_bom_atravessa() {
        String corpo = pedirEquipeCom(IDENTITY.tokenDe(UUID.randomUUID()))
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(corpo).contains("\"servico\":\"merchant\"");
    }

    // ── o gateway não autoriza, e isso é asserção ───────────────────────────

    @Test
    void o_gateway_deixa_passar_token_de_quem_nao_tem_vinculo_com_a_loja() {
        // Um usuário qualquer, sem vínculo nenhum, atravessa. NÃO é defeito: é a
        // ADR-012 escrita como teste. O gateway não sabe que GET .../team exige
        // GERENCIAR_EQUIPE, e pôr esse mapa aqui duplicaria regra de domínio na
        // borda, onde ela envelhece sem ninguém ver. Quem recusa é o
        // merchant-service, e ele já recusa desde a C-A.
        pedirEquipeCom(IDENTITY.tokenDe(UUID.randomUUID())).expectStatus().isOk();
    }

    // ── CORS ────────────────────────────────────────────────────────────────

    @Test
    void a_origem_configurada_recebe_cabecalho_de_cors() {
        cliente().options().uri(EQUIPE)
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
    }

    @Test
    void origem_desconhecida_nao_recebe_permissao() {
        cliente().options().uri(EQUIPE)
                .header("Origin", "http://site-de-outra-pessoa")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin");
    }

    @Test
    void o_cors_nao_promete_credenciais() {
        // A credencial deste sistema é um cabeçalho Authorization, não um
        // cookie. Ligar allowCredentials não traria nada e abriria a porta para
        // a combinação com origem curinga.
        cliente().options().uri(EQUIPE)
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectHeader().doesNotExist("Access-Control-Allow-Credentials");
    }

    private RestTestClient.ResponseSpec pedirEquipeCom(String token) {
        return cliente().get().uri(EQUIPE)
                .header("Authorization", "Bearer " + token)
                .exchange();
    }
}
