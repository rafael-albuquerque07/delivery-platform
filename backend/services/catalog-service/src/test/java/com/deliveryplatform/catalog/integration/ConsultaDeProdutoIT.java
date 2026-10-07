package com.deliveryplatform.catalog.integration;

import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET …/produtos/{produtoId}} contra o serviço de verdade (G-F).
 *
 * <p>A forma é a do {@code MarcacaoDeDisponibilidadeIT}: os dois dublês por HTTP, a
 * coleção limpa a cada caso, uma loja e uma pessoa novas por caso.
 *
 * <p>O {@code ContratoDeErrosIT} prova que cada código da rota está declarado; este
 * prova o que o código <b>diz</b> — o corpo, as duas visibilidades, e que os dois
 * 404 são o mesmo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsultaDeProdutoIT extends Infraestrutura {

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
        MERCHANT.respondeCom(marli, loja, "ADMINISTRADOR", List.of("VER_PRODUTO"));
    }

    // ── 1 e 2 · o corpo ─────────────────────────────────────────────────────

    @Test
    @DisplayName("1 · o JSON traz os grupos e as opções, com os identificadores — na forma da listagem")
    @SuppressWarnings("unchecked")
    void o_corpo_traz_grupos_e_opcoes() {
        Produto pizza = margheritaPublicada(loja);

        Map<String, Object> corpo = ler(pizza.getId()).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();

        assertThat(corpo).containsEntry("estadoDePublicacao", "ATIVO").containsKey("vendavel");
        List<Map<String, Object>> grupos = (List<Map<String, Object>>) corpo.get("gruposDeOpcoes");
        assertThat(grupos).hasSize(1);
        List<Map<String, Object>> opcoes = (List<Map<String, Object>>) grupos.get(0).get("opcoes");
        assertThat(opcoes).hasSize(3);
        for (Map<String, Object> opcao : opcoes) {
            assertThat(opcao).containsKeys("id", "nome", "acrescimo", "ordem", "disponibilidade");
            assertThat(UUID.fromString((String) opcao.get("id"))).isNotNull();
        }

        // A mesma representação que a listagem já dá: disponibilidade é o estado em
        // texto, e preço é número. Duas formas do mesmo dado é como elas divergem.
        Map<String, Object> pagina = cliente().get()
                .uri("/api/v1/merchants/" + loja + "/catalog/produtos")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        Map<String, Object> resumo = ((List<Map<String, Object>>) pagina.get("conteudo")).get(0);
        assertThat(corpo.get("disponibilidade")).isEqualTo(resumo.get("disponibilidade")).isInstanceOf(String.class);
        assertThat(corpo.get("precoBase")).isEqualTo(resumo.get("precoBase")).isInstanceOf(Number.class);
        assertThat(opcoes.get(0).get("disponibilidade")).isInstanceOf(String.class);
        assertThat(opcoes.get(0).get("acrescimo")).isInstanceOf(Number.class);
    }

    @Test
    @DisplayName("2 · as opções saem na ordem que o comerciante pôs, não na de inserção")
    @SuppressWarnings("unchecked")
    void opcoes_saem_por_ordem() {
        Produto p = Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA, "Pizza",
                Precos.reais("49.90"), ModoDeControle.QUALITATIVO, 0);
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Tamanho", 1, 1, 0, List.of(
                Opcao.nova("Grande", Precos.reais("16.00"), 2),
                Opcao.nova("Pequena", Precos.reais("0.00"), 0),
                Opcao.nova("Média", Precos.reais("8.00"), 1))));
        p = produtos.salvar(p);

        Map<String, Object> corpo = ler(p.getId()).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();

        List<Map<String, Object>> opcoes = (List<Map<String, Object>>)
                ((List<Map<String, Object>>) corpo.get("gruposDeOpcoes")).get(0).get("opcoes");
        assertThat(opcoes).extracting(o -> o.get("nome")).containsExactly("Pequena", "Média", "Grande");
    }

    // ── 3 e 4 · os dois 404 ─────────────────────────────────────────────────

    @Test
    @DisplayName("3 · produto de outra loja é 404, pelo HTTP")
    void de_outra_loja_e_404() {
        Produto daOutra = margheritaPublicada(UUID.randomUUID());

        ler(daOutra.getId()).expectStatus().isNotFound();
    }

    @Test
    @DisplayName("4 · produto que não existe é 404, com o mesmo detail do de outra loja")
    @SuppressWarnings("unchecked")
    void inexistente_e_404_com_o_mesmo_corpo() {
        Produto daOutra = margheritaPublicada(UUID.randomUUID());

        Map<String, Object> deOutraLoja = ler(daOutra.getId()).expectStatus().isNotFound()
                .expectBody(Map.class).returnResult().getResponseBody();
        Map<String, Object> inexistente = ler(UUID.randomUUID()).expectStatus().isNotFound()
                .expectBody(Map.class).returnResult().getResponseBody();

        assertThat(deOutraLoja.get("detail")).isNotNull().isEqualTo(inexistente.get("detail"));
    }

    // ── 5 · as duas visibilidades ───────────────────────────────────────────

    @Test
    @DisplayName("5 · rascunho sai na rota nova, e a listagem não o mostra")
    @SuppressWarnings("unchecked")
    void rascunho_sai_aqui_e_nao_na_listagem() {
        Produto rascunho = produtos.salvar(Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA,
                "Pizza em teste", Precos.reais("39.90"), ModoDeControle.QUALITATIVO, 0));

        Map<String, Object> corpo = ler(rascunho.getId()).expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(corpo).containsEntry("estadoDePublicacao", "RASCUNHO");

        Map<String, Object> pagina = cliente().get()
                .uri("/api/v1/merchants/" + loja + "/catalog/produtos")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat((List<Object>) pagina.get("conteudo")).isEmpty();
    }

    // ── 6 · a recusa ────────────────────────────────────────────────────────

    @Test
    @DisplayName("6 · sem VER_PRODUTO, 403 — com o produto existindo")
    void sem_permissao_e_403() {
        Produto pizza = margheritaPublicada(loja);
        MERCHANT.respondeCom(marli, loja, "COLABORADOR", List.of("ALTERAR_PRODUTO"));

        ler(pizza.getId()).expectStatus().isForbidden();
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    private RestTestClient cliente() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    private RestTestClient.ResponseSpec ler(UUID produtoId) {
        return cliente().get()
                .uri("/api/v1/merchants/" + loja + "/catalog/produtos/" + produtoId)
                .header("Authorization", "Bearer " + token)
                .exchange();
    }

    private Produto margheritaPublicada(UUID daLoja) {
        Produto p = Produto.rascunho(daLoja, ProdutoDeTeste.CATEGORIA, "Pizza margherita",
                Precos.reais("49.90"), ModoDeControle.QUALITATIVO, 0);
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(3));
        p.publicar();
        return produtos.salvar(p);
    }
}
