package com.deliveryplatform.catalog.integration;

import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
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
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code POST .../produtos/{produtoId}/grupos} e
 * {@code POST .../produtos/{produtoId}/grupos/{grupoId}/opcoes} (ADR-077), contra o
 * Mongo de verdade. O que entrou se afirma <b>pelo banco</b>.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MontagemDeOpcoesIT extends Infraestrutura {

    private static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();
    private static final MerchantDeMentira MERCHANT = MerchantDeMentira.subir();

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
        loja = UUID.randomUUID();
        marli = UUID.randomUUID();
        token = IDENTITY.tokenDe(marli);
        MERCHANT.respondeCom(marli, loja, "ADMINISTRADOR", List.of("VER_PRODUTO", "ALTERAR_PRODUTO"));
    }

    @Test
    @DisplayName("o rascunho ganha um grupo vazio, e depois a opção dentro dele — pelo banco")
    void grupo_e_opcao_em_rascunho() {
        Produto pizza = rascunho();

        criarGrupo(loja, pizza.getId(), grupo("Tamanho", 1, 1, 0))
                .expectStatus().isCreated()
                .expectHeader().doesNotExist("Location")
                .expectBody()
                .jsonPath("$.gruposDeOpcoes[0].nome").isEqualTo("Tamanho")
                .jsonPath("$.gruposDeOpcoes[0].opcoes").isEmpty();
        UUID grupoId = gruposNoBanco(pizza).get(0).id();

        criarOpcao(loja, pizza.getId(), grupoId, opcao("Grande", "16.00", 2))
                .expectStatus().isCreated()
                .expectHeader().doesNotExist("Location")
                .expectBody()
                .jsonPath("$.gruposDeOpcoes[0].opcoes[0].nome").isEqualTo("Grande");

        List<GrupoDeOpcoes> grupos = gruposNoBanco(pizza);
        assertThat(grupos).singleElement().satisfies(g -> {
            assertThat(g.nome()).isEqualTo("Tamanho");
            assertThat(g.minEscolhas()).isEqualTo(1);
            assertThat(g.maxEscolhas()).isEqualTo(1);
            assertThat(g.opcoes()).singleElement().satisfies(o -> {
                assertThat(o.nome()).isEqualTo("Grande");
                assertThat(o.acrescimo()).isEqualTo(Precos.reais("16.00"));
                assertThat(o.disponivel()).isTrue();
            });
        });
    }

    /**
     * O que a ADR-077 abre: a marcação da opção existia desde a G-C, e até aqui nenhuma
     * opção nascia pela rota. Uma opção criada pela rota é marcável pela rota.
     */
    @Test
    @DisplayName("a opção criada pela rota é marcável pela rota que já existia")
    void opcao_criada_e_marcavel() {
        Produto pizza = rascunho();
        criarGrupo(loja, pizza.getId(), grupo("Tamanho", 1, 1, 0)).expectStatus().isCreated();
        UUID grupoId = gruposNoBanco(pizza).get(0).id();
        criarOpcao(loja, pizza.getId(), grupoId, opcao("Calabresa", "0.00", 0))
                .expectStatus().isCreated();
        UUID opcaoId = gruposNoBanco(pizza).get(0).opcoes().get(0).id();

        cliente().put()
                .uri(base(loja) + "/" + pizza.getId() + "/grupos/" + grupoId + "/opcoes/"
                        + opcaoId + "/disponibilidade")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"estado\":\"ESGOTADO_INDETERMINADO\"}")
                .exchange()
                .expectStatus().isOk();

        assertThat(gruposNoBanco(pizza).get(0).opcoes().get(0).disponibilidade().estado())
                .isEqualTo(EstadoDeDisponibilidade.ESGOTADO_INDETERMINADO);
    }

    @Test
    @DisplayName("C3: mínimo maior que o máximo é 400 com a frase do grupo, e nada entra")
    void c3_e_400() {
        Produto pizza = rascunho();

        ProblemDetail recusa = criarGrupo(loja, pizza.getId(), grupo("Tamanho", 2, 1, 0))
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();

        assertThat(recusa.getDetail()).contains("«Tamanho»").doesNotContain("C3");
        assertThat(gruposNoBanco(pizza)).isEmpty();
    }

    @Test
    @DisplayName("corpo sem maxEscolhas é 400 de forma, antes do agregado")
    void corpo_incompleto_e_400() {
        Produto pizza = rascunho();

        criarGrupo(loja, pizza.getId(), "{\"nome\":\"Tamanho\",\"minEscolhas\":1,\"ordem\":0}")
                .expectStatus().isBadRequest();

        assertThat(gruposNoBanco(pizza)).isEmpty();
    }

    @Test
    @DisplayName("produto de outra loja responde igual a produto que não existe (ADR-056), e nada muda lá")
    void outra_loja_e_igual_a_inexistente() {
        Produto daVizinha = produtos.salvar(Produto.rascunho(UUID.randomUUID(),
                ProdutoDeTeste.CATEGORIA, "X-Burguer", Precos.reais("20.00"),
                ModoDeControle.QUALITATIVO, 0));

        ProblemDetail deOutraLoja = criarGrupo(loja, daVizinha.getId(), grupo("Tamanho", 1, 1, 0))
                .expectStatus().isNotFound()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();
        ProblemDetail inexistente = criarGrupo(loja, UUID.randomUUID(), grupo("Tamanho", 1, 1, 0))
                .expectStatus().isNotFound()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();

        assertThat(deOutraLoja.getDetail()).isEqualTo(inexistente.getDetail());
        assertThat(gruposNoBanco(daVizinha))
                .as("quem tem ALTERAR_PRODUTO aqui não monta o cardápio de lá")
                .isEmpty();
    }

    @Test
    @DisplayName("grupo que não é deste produto é 404 com a frase do grupo")
    void grupo_inexistente_e_404() {
        Produto pizza = rascunho();

        ProblemDetail recusa = criarOpcao(loja, pizza.getId(), UUID.randomUUID(),
                opcao("Grande", "16.00", 0))
                .expectStatus().isNotFound()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();

        assertThat(recusa.getDetail())
                .isEqualTo("este grupo não existe mais neste produto; recarregue o produto");
    }

    @Test
    @DisplayName("sem ALTERAR_PRODUTO é 403, e nada entra")
    void sem_alterar_produto_e_403() {
        Produto pizza = rascunho();
        MERCHANT.respondeCom(marli, loja, "COLABORADOR", List.of("VER_PRODUTO", "CRIAR_PRODUTO"));

        criarGrupo(loja, pizza.getId(), grupo("Tamanho", 1, 1, 0)).expectStatus().isForbidden();

        assertThat(gruposNoBanco(pizza)).isEmpty();
    }

    @Test
    @DisplayName("sem token é 401")
    void sem_token_e_401() {
        Produto pizza = rascunho();

        cliente().post()
                .uri(base(loja) + "/" + pizza.getId() + "/grupos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(grupo("Tamanho", 1, 1, 0))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("ATIVO: grupo vazio é 400 pela C4 recobrada, e o produto no banco fica como estava")
    void ativo_grupo_vazio_e_400() {
        Produto pizza = produtos.salvar(ProdutoDeTeste.margheritaPublicada());
        List<GrupoDeOpcoes> antes = gruposNoBanco(pizza);

        ProblemDetail recusa = criarGrupo(loja(pizza), pizza.getId(), grupo("Bebida", 0, 1, 5))
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();

        assertThat(recusa.getDetail()).contains("«Bebida»").doesNotContain("C4");
        assertThat(gruposNoBanco(pizza)).isEqualTo(antes);
        assertThat(produtos.buscarPorId(pizza.getId()).orElseThrow().getEstadoDePublicacao())
                .isEqualTo(EstadoDePublicacao.ATIVO);
    }

    @Test
    @DisplayName("ATIVO: opção que mantém as invariantes é 201, e o produto segue no cardápio")
    void ativo_opcao_valida_e_201() {
        Produto pizza = produtos.salvar(ProdutoDeTeste.margheritaPublicada());
        UUID tamanho = pizza.getGruposDeOpcoes().get(0).id();

        criarOpcao(loja(pizza), pizza.getId(), tamanho, opcao("Família", "24.00", 3))
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.estadoDePublicacao").isEqualTo("ATIVO");

        assertThat(gruposNoBanco(pizza).get(0).opcoes()).extracting(Opcao::nome)
                .containsExactly("Pequena", "Média", "Grande", "Família");
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    private Produto rascunho() {
        return produtos.salvar(Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA, "Pizza calabresa",
                Precos.reais("45.00"), ModoDeControle.QUALITATIVO, 0));
    }

    /** O {@code margheritaPublicada()} nasce na loja das fixtures; a pessoa ganha vínculo lá. */
    private UUID loja(Produto produto) {
        MERCHANT.respondeCom(marli, produto.getEstabelecimentoId(), "ADMINISTRADOR",
                List.of("VER_PRODUTO", "ALTERAR_PRODUTO"));
        return produto.getEstabelecimentoId();
    }

    private List<GrupoDeOpcoes> gruposNoBanco(Produto produto) {
        return produtos.buscarPorId(produto.getId()).orElseThrow().getGruposDeOpcoes();
    }

    private static String grupo(String nome, int min, int max, int ordem) {
        return "{\"nome\":\"%s\",\"minEscolhas\":%d,\"maxEscolhas\":%d,\"ordem\":%d}"
                .formatted(nome, min, max, ordem);
    }

    private static String opcao(String nome, String acrescimo, int ordem) {
        return "{\"nome\":\"%s\",\"acrescimo\":%s,\"ordem\":%d}"
                .formatted(nome, new BigDecimal(acrescimo).toPlainString(), ordem);
    }

    private String base(UUID daLoja) {
        return "/api/v1/merchants/" + daLoja + "/catalog/produtos";
    }

    private RestTestClient cliente() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    private RestTestClient.ResponseSpec criarGrupo(UUID daLoja, UUID produtoId, String corpo) {
        return cliente().post()
                .uri(base(daLoja) + "/" + produtoId + "/grupos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .exchange();
    }

    private RestTestClient.ResponseSpec criarOpcao(UUID daLoja, UUID produtoId, UUID grupoId,
                                                   String corpo) {
        return cliente().post()
                .uri(base(daLoja) + "/" + produtoId + "/grupos/" + grupoId + "/opcoes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .exchange();
    }
}
