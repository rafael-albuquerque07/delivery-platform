package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Operacao;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.domain.model.TipoDeOperacao;
import com.deliveryplatform.merchant.support.IdentityDeMentira;
import com.deliveryplatform.merchant.support.Infraestrutura;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import com.deliveryplatform.valuetypes.Money;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As condições do pedido, atravessando a cadeia inteira (ADR-087).
 *
 * <p>A pizzaria de teste tem três áreas ({@code LojaDeTeste.areas()}): <b>Boa Viagem</b>, ativa, a
 * R$ 7,00, com a faixa de CEP 51000-000 a 51999-999; <b>Centro</b>, ativa, de graça, sem faixa; e
 * <b>Pina</b>, desativada, a R$ 9,00, com a faixa 50000-000 a 50999-999. Entrega: dinheiro, cartão
 * e Pix, mínimo de R$ 25,00. Retirada: dinheiro e cartão, sem mínimo, desconto de R$ 5,00. Troco:
 * fundo de R$ 50,00, sem aceitar pedido sem troco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "delivery.outbox.habilitado=false")
class CondicoesDoPedidoControllerIT extends Infraestrutura {

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

    private UUID loja;
    private String token;

    @BeforeEach
    void umaLoja() {
        loja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        token = tokenDeMembro(loja);
    }

    private String tokenDeMembro(UUID daLoja) {
        UUID pessoa = UUID.randomUUID();
        Instant agora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        membros.salvar(Membro.reconstituir(UUID.randomUUID(), pessoa, daLoja, Papel.COLABORADOR,
                EnumSet.of(Permissao.VER_PEDIDO), EstadoDoMembro.ATIVO, agora, agora));
        return IDENTITY.tokenDe(pessoa);
    }

    private RestTestClient.ResponseSpec pedir(UUID daLoja, String corpo, String comToken) {
        var requisicao = RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build()
                .post().uri("/internal/merchants/" + daLoja + "/condicoes-do-pedido")
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo);
        if (comToken != null) {
            requisicao = requisicao.header("Authorization", "Bearer " + comToken);
        }
        return requisicao.exchange();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> condicoes(String corpo) {
        return pedir(loja, corpo, token).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
    }

    private static BigDecimal dinheiro(Object valor) {
        return new BigDecimal(String.valueOf(valor));
    }

    @Test
    @DisplayName("5 · retirada: métodos, mínimo — zero quando é zero —, desconto e troco")
    @SuppressWarnings("unchecked")
    void retirada() {
        Map<String, Object> resposta = condicoes("{\"modalidade\":\"RETIRADA\"}");

        assertThat(resposta.get("aceita")).isEqualTo(true);
        assertThat((List<String>) resposta.get("metodos")).containsExactlyInAnyOrder("DINHEIRO", "CARTAO");
        assertThat(dinheiro(resposta.get("pedidoMinimo"))).isEqualByComparingTo("0");
        assertThat(dinheiro(resposta.get("descontoDeRetirada"))).isEqualByComparingTo("5.00");
        Map<String, Object> troco = (Map<String, Object>) resposta.get("politicaDeTroco");
        assertThat(dinheiro(troco.get("fundoMaximoDeTroco"))).isEqualByComparingTo("50.00");
        assertThat(troco.get("aceitaPedidoSemTrocoDisponivel")).isEqualTo(false);
        assertThat(resposta).doesNotContainKeys("atendida", "area", "taxaDeEntrega", "bairrosAtendidos");
    }

    @Test
    @DisplayName("6 · entrega por bairro atendido: a área e a taxa, sem desconto (I12)")
    @SuppressWarnings("unchecked")
    void entrega_por_bairro_atendido() {
        Map<String, Object> resposta = condicoes("{\"modalidade\":\"ENTREGA\",\"bairro\":\"boa viagem\"}");

        assertThat(resposta.get("atendida")).isEqualTo(true);
        assertThat(resposta.get("area")).isEqualTo("Boa Viagem");
        assertThat(dinheiro(resposta.get("taxaDeEntrega"))).isEqualByComparingTo("7.00");
        assertThat((List<String>) resposta.get("metodos")).containsExactlyInAnyOrder("DINHEIRO", "CARTAO", "PIX");
        assertThat(dinheiro(resposta.get("pedidoMinimo"))).isEqualByComparingTo("25.00");
        assertThat(resposta).containsKey("politicaDeTroco");
        assertThat(resposta).doesNotContainKeys("descontoDeRetirada", "bairrosAtendidos");
    }

    @Test
    @DisplayName("7 · entrega por CEP numa faixa atendida: a área e a taxa")
    void entrega_por_cep_atendido() {
        Map<String, Object> resposta = condicoes("{\"modalidade\":\"ENTREGA\",\"cep\":\"51020-000\"}");

        assertThat(resposta.get("atendida")).isEqualTo(true);
        assertThat(resposta.get("area")).isEqualTo("Boa Viagem");
        assertThat(dinheiro(resposta.get("taxaDeEntrega"))).isEqualByComparingTo("7.00");
    }

    @Test
    @DisplayName("8 · bairro não atendido: 200, atendida falso, sem taxa (M11), e os bairros atendidos (ADR-020)")
    void bairro_nao_atendido() {
        Map<String, Object> resposta = condicoes("{\"modalidade\":\"ENTREGA\",\"bairro\":\"Afogados\"}");

        assertThat(resposta.get("atendida")).isEqualTo(false);
        assertThat(resposta).doesNotContainKeys("taxaDeEntrega", "area");
        assertThat(resposta.get("bairrosAtendidos")).isEqualTo(List.of("Boa Viagem", "Centro"));
    }

    @Test
    @DisplayName("9 · área inativa não cota, nem pelo nome nem pelo CEP, e não aparece na lista (ADR-020)")
    @SuppressWarnings("unchecked")
    void area_inativa_nao_cota() {
        Map<String, Object> peloNome = condicoes("{\"modalidade\":\"ENTREGA\",\"bairro\":\"Pina\"}");
        Map<String, Object> peloCep = condicoes("{\"modalidade\":\"ENTREGA\",\"cep\":\"50100-000\"}");

        for (Map<String, Object> resposta : List.of(peloNome, peloCep)) {
            assertThat(resposta.get("atendida")).isEqualTo(false);
            assertThat(resposta).doesNotContainKeys("taxaDeEntrega", "area");
            assertThat((List<String>) resposta.get("bairrosAtendidos")).doesNotContain("Pina");
        }
    }

    @Test
    @DisplayName("10 · área com taxa zero: atendida, e a taxa é zero — entrega grátis não é área ausente")
    void taxa_zero_e_atendida() {
        Map<String, Object> resposta = condicoes("{\"modalidade\":\"ENTREGA\",\"bairro\":\"Centro\"}");

        assertThat(resposta.get("atendida")).isEqualTo(true);
        assertThat(resposta.get("area")).isEqualTo("Centro");
        assertThat(dinheiro(resposta.get("taxaDeEntrega"))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("11 · modalidade que a loja não aceita: 200, aceita falso, e nada mais")
    void modalidade_nao_aceita() {
        Operacao soRetirada = new Operacao(TipoDeOperacao.SEPARACAO,
                Map.of(Modalidade.RETIRADA, Set.of(MetodoPagamento.DINHEIRO)),
                Money.ZERO,
                Map.of(Modalidade.RETIRADA, Money.ZERO));
        UUID balcao = lojas.salvar(Estabelecimento.novo(
                LojaDeTeste.identificacao(FusoHorario.PADRAO), soRetirada, LojaDeTeste.troco(),
                LojaDeTeste.disponibilidade(), List.of())).getId();

        @SuppressWarnings("unchecked")
        Map<String, Object> resposta = pedir(balcao, "{\"modalidade\":\"ENTREGA\",\"bairro\":\"Centro\"}",
                tokenDeMembro(balcao))
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        assertThat(resposta).containsOnlyKeys("estabelecimentoId", "modalidade", "aceita");
        assertThat(resposta.get("aceita")).isEqualTo(false);
    }

    @Test
    @DisplayName("12 · entrega sem bairro e sem CEP: 400, com a frase do tratador")
    void entrega_sem_endereco_e_400() {
        pedir(loja, "{\"modalidade\":\"ENTREGA\"}", token)
                .expectStatus().isBadRequest()
                .expectBody(String.class).value(corpo ->
                        assertThat(corpo).contains("para entrega, informe o bairro ou o CEP"));
    }

    @Test
    @DisplayName("CEP que não é CEP: 400 com a frase, e não 500")
    void cep_invalido_e_400() {
        pedir(loja, "{\"modalidade\":\"ENTREGA\",\"cep\":\"123\"}", token)
                .expectStatus().isBadRequest()
                .expectBody(String.class).value(corpo ->
                        assertThat(corpo).contains("um CEP precisa ter oito dígitos"));
    }

    @Test
    @DisplayName("sem modalidade: 400 de forma, antes do caso de uso")
    void sem_modalidade_e_400() {
        pedir(loja, "{\"bairro\":\"Centro\"}", token).expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("sem vínculo, e loja inexistente: o mesmo 403 da rota irmã")
    void sem_vinculo_e_403() {
        String estranho = IDENTITY.tokenDe(UUID.randomUUID());
        pedir(loja, "{\"modalidade\":\"RETIRADA\"}", estranho).expectStatus().isForbidden();
        pedir(UUID.randomUUID(), "{\"modalidade\":\"RETIRADA\"}", estranho).expectStatus().isForbidden();
    }

    @Test
    void sem_token_e_401() {
        pedir(loja, "{\"modalidade\":\"RETIRADA\"}", null).expectStatus().isUnauthorized();
    }
}
