package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.AreaDeEntrega;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.support.EquipeDeTeste;
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
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /settings/areas-entrega} (ADR-081), de ponta a ponta no {@code merchant}: gravação pelo
 * repositório de verdade, leitura de volta, as recusas do domínio com a frase do tratador, e a
 * autorização no molde do horário.
 *
 * <p>A loja do teste nasce com as três áreas do {@code LojaDeTeste}: Boa Viagem (7,00, com
 * faixa), Centro (zero) e Pina (9,00, desativada, com faixa).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "delivery.outbox.habilitado=false",
        "delivery.expediente.varredura-habilitada=false"})
class AreasDeEntregaIT extends Infraestrutura {

    private static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();

    static final String DUAS_AREAS = """
            {"areasDeEntrega":[
              {"nome":"Afogados","taxa":4.50,"ativa":true,"faixasDeCep":[{"inicio":"50750-000","fim":"50770-999"}]},
              {"nome":"Centro","taxa":0,"ativa":true,"faixasDeCep":[]}]}""";

    private static final String CAMINHO = "/api/v1/merchants/%s/settings/areas-entrega";
    private static final String FRASE_DO_CONFLITO =
            "duas áreas não podem ter o mesmo nome, e as faixas de CEP de áreas ativas não podem se cruzar";

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

    @Autowired MembroRepositorio membros;
    @Autowired EstabelecimentoRepositorio lojas;

    private UUID loja;
    private UUID marli;
    private UUID bia;

    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @BeforeEach
    void umaPizzaria() {
        loja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        marli = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora())).getUsuarioId();
        bia = membros.salvar(Membro.colaborador(
                UUID.randomUUID(), loja, EquipeDeTeste.DA_BIA, agora())).getUsuarioId();
    }

    private Estabelecimento gravada(UUID qual) {
        return lojas.buscarPorId(qual).orElseThrow();
    }

    @Test
    @DisplayName("1 · grava duas áreas pelo repositório de verdade, e o GET as lê de volta, com a taxa de cada uma")
    void grava_e_le() {
        put(loja, marli, DUAS_AREAS).expectStatus().isOk();

        assertThat(gravada(loja).getAreasDeEntrega()).extracting(AreaDeEntrega::nome)
                .containsExactlyInAnyOrder("Afogados", "Centro");
        assertThat(gravada(loja).areaPara("50760-123")).map(AreaDeEntrega::nome).contains("Afogados");

        Map<String, Object> lido = get(loja, marli).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        List<Map<String, Object>> areas = lista(lido);
        assertThat(areas).extracting(a -> a.get("nome")).containsExactly("Afogados", "Centro");
        assertThat(new BigDecimal(areas.get(0).get("taxa").toString())).isEqualByComparingTo("4.50");
        assertThat(new BigDecimal(areas.get(1).get("taxa").toString())).isEqualByComparingTo("0");
    }

    /**
     * A M11 com as palavras dela: "Ausência de área ≠ taxa zero". O Centro tem taxa zero e é
     * entrega grátis; a Boa Viagem — ativa, com taxa e faixa antes do {@code PUT} — saiu da lista,
     * e passa a não ser entrega nenhuma: nem pelo nome, nem pelo CEP, e nunca por zero.
     */
    @Test
    @DisplayName("2 · bairro sem área não é entrega grátis: o Centro é zero, e a Boa Viagem que saiu não existe")
    void bairro_sem_area_nao_e_entrega_gratis() {
        assertThat(gravada(loja).areaPorNome("Boa Viagem")).as("a premissa: ela existia").isPresent();

        put(loja, marli, DUAS_AREAS).expectStatus().isOk();

        Estabelecimento depois = gravada(loja);
        assertThat(depois.areaPorNome("Centro")).map(AreaDeEntrega::taxa).contains(Money.ZERO);
        assertThat(depois.areaPorNome("Boa Viagem")).isEmpty();
        assertThat(depois.areaPara("51500-000")).as("a faixa da Boa Viagem saiu com ela").isEmpty();

        Map<String, Object> lido = get(loja, marli).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(lista(lido)).extracting(a -> a.get("nome")).doesNotContain("Boa Viagem");
    }

    @Test
    @DisplayName("3 · duas áreas com o mesmo nome normalizado: 400 com a frase do tratador, e nada muda (M9)")
    void m9_e_400() {
        ProblemDetail recusa = put(loja, marli, """
                {"areasDeEntrega":[
                  {"nome":"Boa Viagem","taxa":7,"ativa":true,"faixasDeCep":[]},
                  {"nome":"boa  viágem","taxa":8,"ativa":false,"faixasDeCep":[]}]}""")
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();

        assertThat(recusa.getDetail()).isEqualTo(FRASE_DO_CONFLITO);
        assertThat(gravada(loja).getAreasDeEntrega()).containsExactlyInAnyOrderElementsOf(LojaDeTeste.areas());
    }

    @Test
    @DisplayName("4 · faixas que se cruzam entre áreas ativas: 400, e nada muda (M10)")
    void m10_e_400() {
        ProblemDetail recusa = put(loja, marli, """
                {"areasDeEntrega":[
                  {"nome":"Boa Viagem","taxa":7,"ativa":true,"faixasDeCep":[{"inicio":"51000-000","fim":"51999-999"}]},
                  {"nome":"Pina","taxa":9,"ativa":true,"faixasDeCep":[{"inicio":"51500-000","fim":"51600-000"}]}]}""")
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();

        assertThat(recusa.getDetail()).isEqualTo(FRASE_DO_CONFLITO);
        assertThat(gravada(loja).getAreasDeEntrega()).containsExactlyInAnyOrderElementsOf(LojaDeTeste.areas());
    }

    @Test
    @DisplayName("4b · as mesmas faixas com uma área desativada são aceitas, e ela volta desativada")
    void desativada_nao_entra_na_m10() {
        put(loja, marli, """
                {"areasDeEntrega":[
                  {"nome":"Boa Viagem","taxa":7,"ativa":true,"faixasDeCep":[{"inicio":"51000-000","fim":"51999-999"}]},
                  {"nome":"Pina","taxa":9,"ativa":false,"faixasDeCep":[{"inicio":"51500-000","fim":"51600-000"}]}]}""")
                .expectStatus().isOk();

        Map<String, Object> lido = get(loja, marli).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(lista(lido)).filteredOn(a -> "Pina".equals(a.get("nome")))
                .singleElement().satisfies(a -> assertThat(a.get("ativa")).isEqualTo(false));
        assertThat(gravada(loja).areaPorNome("Pina")).as("desativada não cota").isEmpty();
    }

    @Test
    @DisplayName("4c · CEP que não é CEP e taxa negativa: 400, cada um com a sua frase")
    void cep_e_taxa_invalidos() {
        ProblemDetail cep = put(loja, marli, """
                {"areasDeEntrega":[{"nome":"Pina","taxa":9,"ativa":true,"faixasDeCep":[{"inicio":"123","fim":"51600-000"}]}]}""")
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();
        assertThat(cep.getDetail()).isEqualTo("um CEP precisa ter oito dígitos, e a faixa começa antes de terminar");

        ProblemDetail taxa = put(loja, marli, """
                {"areasDeEntrega":[{"nome":"Pina","taxa":-1,"ativa":true,"faixasDeCep":[]}]}""")
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();
        assertThat(taxa.getDetail()).startsWith("a taxa de uma área não pode ser negativa");
    }

    @Test
    @DisplayName("5 · lista vazia é aceita, e volta vazia")
    void vazia_e_aceita() {
        put(loja, marli, "{\"areasDeEntrega\":[]}").expectStatus().isOk();

        assertThat(gravada(loja).getAreasDeEntrega()).isEmpty();
        Map<String, Object> lido = get(loja, marli).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(lista(lido)).isEmpty();
    }

    @Test
    @DisplayName("6 · sem token é 401; com token e sem CONFIGURAR_LOJA é 403 — nas duas operações")
    void sem_token_e_sem_permissao() {
        cliente().get().uri(CAMINHO.formatted(loja)).exchange().expectStatus().isUnauthorized();
        cliente().put().uri(CAMINHO.formatted(loja))
                .contentType(MediaType.APPLICATION_JSON).body(DUAS_AREAS)
                .exchange().expectStatus().isUnauthorized();

        get(loja, bia).expectStatus().isForbidden();
        ProblemDetail recusa = put(loja, bia, DUAS_AREAS)
                .expectStatus().isForbidden()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();
        assertThat(recusa.getDetail()).isEqualTo("sem acesso a este estabelecimento");
        assertThat(gravada(loja).getAreasDeEntrega()).containsExactlyInAnyOrderElementsOf(LojaDeTeste.areas());
    }

    /** O precedente é o caso 12 do {@code DefinirHorarioIT}: o 403 de M7, idêntico a "não existe". */
    @Test
    @DisplayName("7 · loja de outra pessoa dá o mesmo 403 que loja inexistente — corpo e status")
    void outra_loja_e_igual_a_inexistente() {
        UUID vizinha = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        membros.salvar(Membro.fundador(UUID.randomUUID(), vizinha, agora()));

        String deOutra = put(vizinha, marli, DUAS_AREAS)
                .expectStatus().isForbidden()
                .expectBody(String.class).returnResult().getResponseBody();
        String inexistente = put(UUID.randomUUID(), marli, DUAS_AREAS)
                .expectStatus().isForbidden()
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(semInstance(deOutra)).isEqualTo(semInstance(inexistente));
        assertThat(gravada(vizinha).getAreasDeEntrega()).containsExactlyInAnyOrderElementsOf(LojaDeTeste.areas());
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Map<String, Object> corpo) {
        return (List<Map<String, Object>>) corpo.get("areasDeEntrega");
    }

    private RestTestClient.ResponseSpec put(UUID estabelecimento, UUID quem, String corpo) {
        return cliente().put().uri(CAMINHO.formatted(estabelecimento))
                .header("Authorization", "Bearer " + IDENTITY.tokenDe(quem))
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .exchange();
    }

    private RestTestClient.ResponseSpec get(UUID estabelecimento, UUID quem) {
        return cliente().get().uri(CAMINHO.formatted(estabelecimento))
                .header("Authorization", "Bearer " + IDENTITY.tokenDe(quem))
                .exchange();
    }

    private RestTestClient cliente() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    private static String semInstance(String corpo) {
        return corpo.replaceAll("\"instance\":\"[^\"]*\"", "");
    }
}
