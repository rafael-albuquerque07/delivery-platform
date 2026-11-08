package com.deliveryplatform.catalog.integration;

import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code DELETE .../produtos/{produtoId}/publicacao} (ADR-078), contra o Mongo de
 * verdade. O estado se afirma <b>pelo banco</b>.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DespublicacaoDeProdutoIT extends Infraestrutura {

    private static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();
    private static final MerchantDeMentira MERCHANT = MerchantDeMentira.subir();

    private static final List<String> ADMINISTRADOR =
            List.of("VER_PRODUTO", "ALTERAR_PRODUTO", "DESATIVAR_PRODUTO");

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

    private UUID marli;
    private String token;

    @BeforeEach
    void umaPessoaNova() {
        mongo.getCollection("produtos").deleteMany(new Document());
        marli = UUID.randomUUID();
        token = IDENTITY.tokenDe(marli);
        MERCHANT.respondeCom(marli, ProdutoDeTeste.LOJA, "ADMINISTRADOR", ADMINISTRADOR);
    }

    @Test
    @DisplayName("o publicado sai do cardápio, e a disponibilidade fica como estava — pelo banco")
    void publicado_sai_do_ar() {
        Produto pizza = produtos.salvar(ProdutoDeTeste.esgotadoHojeEm(LocalDate.of(2026, 11, 7)));

        despublicar(pizza.getId()).expectStatus().isOk()
                .expectBody().jsonPath("$.estadoDePublicacao").isEqualTo("INATIVO");

        Produto noBanco = noBanco(pizza);
        assertThat(noBanco.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.INATIVO);
        assertThat(noBanco.getDisponibilidade().estado()).isEqualTo(EstadoDeDisponibilidade.ESGOTADO_HOJE);
        assertThat(noBanco.getDisponibilidade().expedienteDeReferencia())
                .isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    @DisplayName("tirar do ar duas vezes — 200 nas duas, e o estado é o mesmo")
    void duas_vezes_e_200_nas_duas() {
        Produto pizza = produtos.salvar(ProdutoDeTeste.margheritaPublicada());

        despublicar(pizza.getId()).expectStatus().isOk();
        despublicar(pizza.getId()).expectStatus().isOk()
                .expectBody().jsonPath("$.estadoDePublicacao").isEqualTo("INATIVO");

        assertThat(noBanco(pizza).getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.INATIVO);
    }

    @Test
    @DisplayName("rascunho é 400 com a frase do rascunho, e continua rascunho")
    void rascunho_e_400() {
        Produto rascunho = produtos.salvar(ProdutoDeTeste.margherita());

        ProblemDetail recusa = despublicar(rascunho.getId())
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();

        assertThat(recusa.getDetail()).isEqualTo(
                "este produto ainda é rascunho e nunca apareceu no cardápio; não há o que tirar do ar");
        assertThat(noBanco(rascunho).getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.RASCUNHO);
    }

    /**
     * O que prova que a permissão é a {@code DESATIVAR_PRODUTO}: quem pode alterar o
     * produto, e publicá-lo, não o tira do ar por isso.
     */
    @Test
    @DisplayName("com ALTERAR_PRODUTO e sem DESATIVAR_PRODUTO é 403, e o produto segue no cardápio")
    void sem_desativar_produto_e_403() {
        Produto pizza = produtos.salvar(ProdutoDeTeste.margheritaPublicada());
        MERCHANT.respondeCom(marli, ProdutoDeTeste.LOJA, "COLABORADOR",
                List.of("VER_PRODUTO", "ALTERAR_PRODUTO"));

        despublicar(pizza.getId()).expectStatus().isForbidden();

        assertThat(noBanco(pizza).getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.ATIVO);
    }

    @Test
    @DisplayName("sem token é 401")
    void sem_token_e_401() {
        Produto pizza = produtos.salvar(ProdutoDeTeste.margheritaPublicada());

        cliente().delete()
                .uri(base() + "/" + pizza.getId() + "/publicacao")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("produto de outra loja responde igual a produto que não existe (ADR-056), e segue no cardápio de lá")
    void outra_loja_e_igual_a_inexistente() {
        Produto daVizinha = Produto.rascunho(UUID.randomUUID(), ProdutoDeTeste.CATEGORIA,
                "X-Burguer", Precos.reais("20.00"), ModoDeControle.QUALITATIVO, 0);
        daVizinha.publicar();
        daVizinha = produtos.salvar(daVizinha);

        ProblemDetail deOutraLoja = despublicar(daVizinha.getId())
                .expectStatus().isNotFound()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();
        ProblemDetail inexistente = despublicar(UUID.randomUUID())
                .expectStatus().isNotFound()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();

        assertThat(deOutraLoja.getDetail()).isEqualTo(inexistente.getDetail());
        assertThat(noBanco(daVizinha).getEstadoDePublicacao())
                .as("quem tem DESATIVAR_PRODUTO aqui não tira do ar o cardápio de lá")
                .isEqualTo(EstadoDePublicacao.ATIVO);
    }

    /**
     * O beco da E-6, fechado pela rota — o caso que diz se esta rodada serviu. Cada passo
     * é uma chamada HTTP, e o resultado final se lê no banco.
     */
    @Test
    @DisplayName("o beco fechado: grupo recusado no ar, tirar do ar, grupo, opção, publicar de novo")
    void o_beco_fechado() {
        Produto pizza = produtos.salvar(ProdutoDeTeste.margheritaPublicada());
        String bebida = "{\"nome\":\"Bebida\",\"minEscolhas\":0,\"maxEscolhas\":1,\"ordem\":5}";

        // 0 · o beco: no ar, o grupo vazio cai na C4 recobrada (ADR-077)
        post(base() + "/" + pizza.getId() + "/grupos", bebida).expectStatus().isBadRequest();

        // 1 · tirar do ar
        despublicar(pizza.getId()).expectStatus().isOk();

        // 2 · o grupo entra — fora do ar não há recobrança
        post(base() + "/" + pizza.getId() + "/grupos", bebida).expectStatus().isCreated();
        GrupoDeOpcoes grupo = noBanco(pizza).getGruposDeOpcoes().stream()
                .filter(g -> g.nome().equals("Bebida")).findFirst().orElseThrow();

        // 3 · publicar com o grupo vazio ainda é recusado: a publicação cobra tudo
        publicar(pizza.getId()).expectStatus().isBadRequest();

        // 4 · a opção entra
        post(base() + "/" + pizza.getId() + "/grupos/" + grupo.id() + "/opcoes",
                "{\"nome\":\"Guaraná\",\"acrescimo\":7.00,\"ordem\":0}").expectStatus().isCreated();

        // 5 · publicar de novo
        publicar(pizza.getId()).expectStatus().isOk();

        Produto fim = noBanco(pizza);
        assertThat(fim.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.ATIVO);
        assertThat(fim.getGruposDeOpcoes()).extracting(GrupoDeOpcoes::nome)
                .containsExactly("Tamanho", "Bebida");
        assertThat(fim.getGruposDeOpcoes().get(1).opcoes()).singleElement()
                .satisfies(o -> assertThat(o.nome()).isEqualTo("Guaraná"));
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    private Produto noBanco(Produto produto) {
        return produtos.buscarPorId(produto.getId()).orElseThrow();
    }

    private String base() {
        return "/api/v1/merchants/" + ProdutoDeTeste.LOJA + "/catalog/produtos";
    }

    private RestTestClient cliente() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    private RestTestClient.ResponseSpec despublicar(UUID produtoId) {
        return cliente().delete()
                .uri(base() + "/" + produtoId + "/publicacao")
                .header("Authorization", "Bearer " + token)
                .exchange();
    }

    private RestTestClient.ResponseSpec publicar(UUID produtoId) {
        return cliente().put()
                .uri(base() + "/" + produtoId + "/publicacao")
                .header("Authorization", "Bearer " + token)
                .exchange();
    }

    private RestTestClient.ResponseSpec post(String uri, String corpo) {
        return cliente().post()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .exchange();
    }
}
