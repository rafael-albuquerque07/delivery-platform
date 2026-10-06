package com.deliveryplatform.catalog.integration;

import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.IdentityDeMentira;
import com.deliveryplatform.catalog.support.Infraestrutura;
import com.deliveryplatform.catalog.support.MerchantDeMentira;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A primeira escrita do {@code catalog}, atravessando a cadeia inteira: o token,
 * a autorização no {@code merchant}, o expediente no {@code merchant}, o
 * agregado, o MongoDB e a resposta.
 *
 * <p>O arranjo é o do {@code ProdutoControllerIT}. O {@code MerchantDeMentira}
 * responde as duas rotas — a do contexto de acesso pelos modos que já tinha, e
 * a do expediente corrente pelo sexto, que nasceu nesta rodada.
 *
 * <p>Cada caso usa loja e pessoa novas: a autorização tem cache em processo
 * (G-B4), e um caso que reaproveitasse o par herdaria a decisão do anterior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarcacaoDeDisponibilidadeIT extends Infraestrutura {

    private static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();
    private static final MerchantDeMentira MERCHANT = MerchantDeMentira.subir();

    private static final LocalDate EXPEDIENTE = LocalDate.parse("2026-10-01");

    @DynamicPropertySource
    static void apontarParaOsDubles(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", IDENTITY::jwksUri);
        registry.add("delivery.jwt.issuer", () -> IdentityDeMentira.EMISSOR);
        registry.add("delivery.jwt.audience", () -> IdentityDeMentira.AUDIENCIA);
        registry.add("delivery.autorizacao.merchant-uri", MERCHANT::uri);
        registry.add("delivery.autorizacao.tempo-de-leitura", () -> "300ms");
    }

    @AfterAll
    static void derrubar() {
        IDENTITY.close();
        MERCHANT.close();
    }

    @LocalServerPort
    int porta;

    @Autowired ProdutoRepositorio produtos;
    @Autowired MongoTemplate mongo;

    private UUID loja;
    private UUID marli;
    private String token;

    @BeforeEach
    void umaLojaEUmaPessoaNovas() {
        mongo.getCollection("produtos").deleteMany(new Document());
        MERCHANT.esquecerOQueRecebeu();
        MERCHANT.expedienteE(EXPEDIENTE);

        loja = UUID.randomUUID();
        marli = UUID.randomUUID();
        token = IDENTITY.tokenDe(marli);
        MERCHANT.respondeCom(marli, loja, "ADMINISTRADOR", List.of("VER_PRODUTO", "ALTERAR_PRODUTO"));
    }

    private RestTestClient cliente() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    private Produto margherita() {
        Produto p = Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA, "Pizza margherita",
                Precos.reais("49.90"), ModoDeControle.QUALITATIVO, 0);
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(3));
        p.publicar();
        return produtos.salvar(p);
    }

    private RestTestClient.ResponseSpec marcar(UUID lojaDaUrl, UUID produtoId, String estado) {
        return cliente().put()
                .uri("/api/v1/merchants/" + lojaDaUrl + "/catalog/produtos/" + produtoId
                        + "/disponibilidade")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"estado\":\"" + estado + "\"}")
                .exchange();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> corpo(RestTestClient.ResponseSpec resposta) {
        return resposta.expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
    }

    // ── o caminho feliz ─────────────────────────────────────────────────────

    @Test
    @DisplayName("ESGOTADO_HOJE: 200, o corpo traz o estado novo, e o banco guarda o carimbo do merchant")
    void marca_e_carimba() {
        Produto pizza = margherita();

        Map<String, Object> resposta = corpo(marcar(loja, pizza.getId(), "ESGOTADO_HOJE"));

        assertThat(resposta.get("disponibilidade")).isEqualTo("ESGOTADO_HOJE");
        Produto gravado = produtos.buscarPorId(pizza.getId()).orElseThrow();
        assertThat(gravado.getDisponibilidade().expedienteDeReferencia())
                .as("o expediente veio do merchant — nenhuma data sai do relógio do catalog")
                .isEqualTo(EXPEDIENTE);
        assertThat(gravado.getDisponibilidade().marcadoEm()).isNotNull();
    }

    @Test
    @DisplayName("o token de quem marcou chega às duas rotas do merchant")
    void o_token_chega_ao_expediente() {
        Produto pizza = margherita();

        marcar(loja, pizza.getId(), "ESGOTADO_HOJE").expectStatus().isOk();

        assertThat(MERCHANT.caminhosRecebidos())
                .contains("/internal/merchants/" + loja + "/expediente-corrente");
        assertThat(MERCHANT.autorizacoesRecebidas())
                .as("ADR-045: a credencial é o token de quem pediu, nas duas chamadas")
                .hasSize(2)
                .containsOnly("Bearer " + token);
    }

    // ── a loja que não abre por horário ─────────────────────────────────────

    @Test
    @DisplayName("loja sem horário + ESGOTADO_HOJE: 409 — o 409 do merchant virou um 409 daqui, e não um 500")
    void sem_horario_esgotado_hoje_e_409() {
        Produto pizza = margherita();
        MERCHANT.semHorario();

        marcar(loja, pizza.getId(), "ESGOTADO_HOJE").expectStatus().isEqualTo(409);

        assertThat(produtos.buscarPorId(pizza.getId()).orElseThrow()
                .getDisponibilidade().estado())
                .isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);
    }

    @Test
    @DisplayName("loja sem horário + ESGOTADO_INDETERMINADO: 200, e sem carimbo")
    void sem_horario_indeterminado_passa() {
        Produto pizza = margherita();
        MERCHANT.semHorario();

        Map<String, Object> resposta = corpo(marcar(loja, pizza.getId(), "ESGOTADO_INDETERMINADO"));

        assertThat(resposta.get("disponibilidade")).isEqualTo("ESGOTADO_INDETERMINADO");
        assertThat(produtos.buscarPorId(pizza.getId()).orElseThrow()
                .getDisponibilidade().marcadoEm())
                .as("o par nasce inteiro ou não nasce")
                .isNull();
    }

    // ── a opção, e a razão de a resposta ter corpo ──────────────────────────

    @Test
    @DisplayName("marcar a última opção de um grupo obrigatório: 200, e o produto volta não vendável")
    void a_opcao_derruba_o_vendavel() {
        Produto pizza = Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA, "Pizza calabresa",
                Precos.reais("52.00"), ModoDeControle.QUALITATIVO, 0);
        pizza.acrescentarGrupo(GrupoDeOpcoes.novo("Tamanho", 1, 1, 0,
                List.of(Opcao.nova("Grande", Precos.reais("0.00"), 0))));
        pizza.publicar();
        pizza = produtos.salvar(pizza);
        GrupoDeOpcoes tamanho = pizza.getGruposDeOpcoes().getFirst();

        Map<String, Object> resposta = corpo(cliente().put()
                .uri("/api/v1/merchants/" + loja + "/catalog/produtos/" + pizza.getId()
                        + "/grupos/" + tamanho.id() + "/opcoes/" + tamanho.opcoes().getFirst().id()
                        + "/disponibilidade")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"estado\":\"ESGOTADO_HOJE\"}")
                .exchange());

        assertThat(resposta.get("vendavel"))
                .as("a tela não consegue derivar isto: o resumo não carrega os grupos")
                .isEqualTo(false);
        assertThat(resposta.get("disponibilidade"))
                .as("o PRODUTO continua disponível — quem acabou foi a opção")
                .isEqualTo("DISPONIVEL");
    }

    // ── a falha fechada ─────────────────────────────────────────────────────

    @Test
    @DisplayName("merchant fora do ar no expediente: 503, e o produto no banco não mudou")
    void expediente_fora_do_ar_e_503_e_nada_muda() {
        Produto pizza = margherita();
        MERCHANT.expedienteEstoura();

        marcar(loja, pizza.getId(), "ACABANDO").expectStatus().isEqualTo(503);

        assertThat(produtos.buscarPorId(pizza.getId()).orElseThrow()
                .getDisponibilidade().estado())
                .as("se o produto mudou, a falha fechada não está fechada — uma queda "
                        + "viraria marcação sem carimbo gravada para sempre")
                .isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);
    }

    // ── as recusas ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("sem ALTERAR_PRODUTO: 403")
    void sem_permissao_e_403() {
        Produto pizza = margherita();
        MERCHANT.respondeCom(marli, loja, "COLABORADOR", List.of("VER_PRODUTO"));

        marcar(loja, pizza.getId(), "ACABANDO").expectStatus().isForbidden();
    }

    @Test
    @DisplayName("produto de outra loja: 403 — quem tem ALTERAR_PRODUTO aqui não marca o cardápio de lá")
    void produto_de_outra_loja_e_403() {
        Produto daLoja = margherita();
        UUID outraLoja = UUID.randomUUID();
        MERCHANT.respondeCom(marli, outraLoja, "ADMINISTRADOR", List.of("ALTERAR_PRODUTO"));

        marcar(outraLoja, daLoja.getId(), "ACABANDO").expectStatus().isForbidden();

        assertThat(produtos.buscarPorId(daLoja.getId()).orElseThrow()
                .getDisponibilidade().estado())
                .isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);
    }

    // ── a borda ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("o mesmo estado duas vezes: 200 nas duas — requisição repetida acontece")
    void mesmo_estado_duas_vezes() {
        Produto pizza = margherita();

        marcar(loja, pizza.getId(), "ESGOTADO_HOJE").expectStatus().isOk();
        marcar(loja, pizza.getId(), "ESGOTADO_HOJE").expectStatus().isOk();
    }

    @Test
    @DisplayName("estado desconhecido no corpo: 400 — a borda com o cliente não tolera o que não entende")
    void estado_desconhecido_e_400() {
        Produto pizza = margherita();

        marcar(loja, pizza.getId(), "ACABOU_GERAL").expectStatus().isBadRequest();
    }
}
