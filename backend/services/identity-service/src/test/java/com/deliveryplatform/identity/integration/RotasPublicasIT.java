package com.deliveryplatform.identity.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.deliveryplatform.identity.support.GeradorDeChaveDeTeste;

/**
 * Prova que o sistema passou a conseguir dar a partida.
 *
 * <p>Antes desta rodada não havia {@code SecurityFilterChain}, e o padrão do
 * Spring Boot exigia token válido em toda rota — inclusive no JWKS que os outros
 * serviços precisam buscar para validar token. Este teste é o que impede a
 * regressão, e é a única forma de a ADR-037 §1 deixar de ser texto.
 *
 * <p>Usa {@code RestTestClient} (Spring Framework 7), não {@code TestRestTemplate}:
 * a classe não existe mais em nenhum artefato do Spring Boot 4.1.1 nem do
 * Spring Framework 7.0.9 — foi substituída por um cliente fluente no estilo
 * {@code WebTestClient}, em {@code org.springframework.test.web.servlet.client}.
 * Nenhum bean é autoconfigurado para injeção; o cliente é construído a partir
 * de {@code @LocalServerPort}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RotasPublicasIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void chaveDeAssinatura(DynamicPropertyRegistry registry) {
        registry.add("delivery.jwt.private-key-path", GeradorDeChaveDeTeste::caminhoDaChaveUnica);
    }

    @LocalServerPort
    private int porta;

    private RestTestClient http;

    @BeforeEach
    void construirCliente() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    @Test
    void o_jwks_e_publico_e_traz_a_chave() {
        var resultado = http.get().uri("/.well-known/jwks.json")
                .exchange()
                .expectBody(String.class)
                .returnResult();

        assertThat(resultado.getStatus())
                .as("JWKS protegido por token trava a partida do sistema inteiro")
                .isEqualTo(HttpStatus.OK);
        assertThat(resultado.getResponseBody()).contains("\"keys\"", "\"kid\"", "\"RSA\"");
    }

    @Test
    void o_jwks_nao_publica_a_chave_privada() {
        String corpo = http.get().uri("/.well-known/jwks.json")
                .exchange()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(corpo)
                .as("d, p, q, dp, dq e qi são o material privado de uma chave RSA em JWK")
                .doesNotContain("\"d\"", "\"p\"", "\"q\"", "\"dp\"", "\"dq\"", "\"qi\"");
    }

    @Test
    void health_e_publico_porque_probe_de_conteiner_nao_carrega_credencial() {
        var resultado = http.get().uri("/actuator/health")
                .exchange()
                .expectBody(String.class)
                .returnResult();

        assertThat(resultado.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rota_de_negocio_sem_token_e_401() {
        var resultado = http.get().uri("/api/v1/qualquer-coisa")
                .exchange()
                .expectBody(String.class)
                .returnResult();

        assertThat(resultado.getStatus())
                .as("o resto é autenticado; 404 aqui significaria que a cadeia não está protegendo nada")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Até a rodada B, este teste esperava 404: o endpoint não existia, e o 404
     * é que provava o permitAll. Com o {@code AutenticacaoController} no ar, a
     * mesma requisição chega à validação de verdade e falha nela — o 400 é
     * quem prova agora que o permitAll casou (401 fecharia a rota) e que o
     * dispatch de erro não é refiltrado (senão viraria 401 de novo).
     */
    @Test
    void login_liberado_por_permitall_chega_a_validacao_e_nao_a_seguranca() {
        var resultado = http.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectBody(String.class)
                .returnResult();

        assertThat(resultado.getStatus())
                .as("400 prova que o permitAll casou -- 401 aqui significaria a rota fechada -- "
                        + "e que o corpo vazio chegou ao AutenticacaoController e falhou no @Valid, "
                        + "não na cadeia de segurança")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
