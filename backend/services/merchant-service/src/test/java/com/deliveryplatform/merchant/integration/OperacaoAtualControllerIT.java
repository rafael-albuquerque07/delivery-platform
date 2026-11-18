package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Pausa;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.support.IdentityDeMentira;
import com.deliveryplatform.merchant.support.Infraestrutura;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A operação da loja, atravessando a cadeia inteira (ADR-087) — na forma do
 * {@link ExpedienteCorrenteControllerIT}.
 *
 * <p>O relógio é fixo numa terça às 20h em São Paulo: a pizzaria de teste está dentro da faixa
 * das 18:00 às 02:00. Com ela pausada, o mesmo instante responde fechada — e é esse o caso que
 * prova que a rota usa a composição do {@code estaAberta}, e não só o horário.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "delivery.outbox.habilitado=false")
class OperacaoAtualControllerIT extends Infraestrutura {

    /** Terça, 29/09/2026, 20:00 em São Paulo — dentro da faixa das 18:00 às 02:00. */
    private static final Instant TERCA_AS_20H = LojaDeTeste.emSaoPaulo("2026-09-29T20:00");

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(TERCA_AS_20H, ZoneOffset.UTC);
        }
    }

    private static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();

    @DynamicPropertySource
    static void apontarParaOIdentityDeMentira(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", IDENTITY::jwksUri);
        registry.add("delivery.jwt.issuer", () -> IdentityDeMentira.EMISSOR);
        registry.add("delivery.jwt.audience", () -> IdentityDeMentira.AUDIENCIA);
    }

    @AfterAll
    static void derrubar() {
        IDENTITY.close();
    }

    @LocalServerPort
    int porta;

    @Autowired
    MembroRepositorio membros;

    @Autowired
    EstabelecimentoRepositorio lojas;

    private UUID aberta;
    private UUID pausada;

    @BeforeEach
    void duasLojas() {
        aberta = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        pausada = lojas.salvar(Estabelecimento.novo(
                LojaDeTeste.identificacao(FusoHorario.PADRAO),
                LojaDeTeste.operacao(),
                LojaDeTeste.troco(),
                LojaDeTeste.disponibilidade().com(Pausa.indefinida("acabou o gás")),
                LojaDeTeste.areas())).getId();
    }

    private RestTestClient.ResponseSpec pedir(UUID loja, String token) {
        var requisicao = RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build()
                .get().uri("/internal/merchants/" + loja + "/operacao");
        if (token != null) {
            requisicao = requisicao.header("Authorization", "Bearer " + token);
        }
        return requisicao.exchange();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> corpo(UUID loja, String token) {
        return pedir(loja, token).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
    }

    private String tokenDeMembro(UUID loja) {
        UUID pessoa = UUID.randomUUID();
        Instant agora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        membros.salvar(Membro.reconstituir(UUID.randomUUID(), pessoa, loja, Papel.COLABORADOR,
                EnumSet.of(Permissao.VER_PEDIDO), EstadoDoMembro.ATIVO, agora, agora));
        return IDENTITY.tokenDe(pessoa);
    }

    @Test
    @DisplayName("1 · membro recebe 200 com aberto e tipoDeOperacao, e nada mais")
    void membro_recebe_a_operacao() {
        Map<String, Object> resposta = corpo(aberta, tokenDeMembro(aberta));

        assertThat(resposta).containsOnlyKeys("estabelecimentoId", "aberto", "tipoDeOperacao");
        assertThat(resposta.get("estabelecimentoId")).isEqualTo(aberta.toString());
        assertThat(resposta.get("tipoDeOperacao")).isEqualTo("PRODUCAO");
    }

    @Test
    @DisplayName("2 · sem vínculo é 403, e loja inexistente é o mesmo 403, com o mesmo corpo")
    void sem_vinculo_e_inexistente_sao_o_mesmo_403() {
        String estranho = IDENTITY.tokenDe(UUID.randomUUID());

        String semVinculo = pedir(aberta, estranho).expectStatus().isForbidden()
                .expectBody(String.class).returnResult().getResponseBody();
        String inexistente = pedir(UUID.randomUUID(), estranho).expectStatus().isForbidden()
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(semVinculo).contains("sem acesso a este estabelecimento");
        assertThat(inexistente.replaceAll("\"instance\":\"[^\"]*\"", ""))
                .isEqualTo(semVinculo.replaceAll("\"instance\":\"[^\"]*\"", ""));
    }

    @Test
    @DisplayName("3 · dentro do horário e pausada: aberto falso — a pausa entra")
    void pausada_dentro_do_horario_esta_fechada() {
        assertThat(corpo(pausada, tokenDeMembro(pausada)).get("aberto")).isEqualTo(false);
    }

    @Test
    @DisplayName("4 · dentro do horário, sem pausa: aberto verdadeiro")
    void dentro_do_horario_sem_pausa_esta_aberta() {
        assertThat(corpo(aberta, tokenDeMembro(aberta)).get("aberto")).isEqualTo(true);
    }

    @Test
    void sem_token_e_401() {
        pedir(aberta, null).expectStatus().isUnauthorized();
    }
}
