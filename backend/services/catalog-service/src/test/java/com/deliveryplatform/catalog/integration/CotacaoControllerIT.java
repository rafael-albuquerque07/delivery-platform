package com.deliveryplatform.catalog.integration;

import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.Disponibilidade;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.IdentityDeMentira;
import com.deliveryplatform.catalog.support.Infraestrutura;
import com.deliveryplatform.catalog.support.MerchantDeMentira;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A cotação do catálogo, pelo HTTP (ADR-088). Os números dos casos são os da tabela da M3-2, e
 * a tabela de fechamento do marco 3 os cita.
 *
 * <p>Cada caso monta os próprios produtos: nenhum produto passa de um caso para outro, e é isso que
 * deixa o caso 19 provar a falta de cache.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CotacaoControllerIT extends Infraestrutura {

    private static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();
    private static final MerchantDeMentira MERCHANT = MerchantDeMentira.subir();

    private static final Instant ONTEM_A_NOITE = Instant.parse("2026-09-25T22:00:00Z");
    private static final LocalDate EXPEDIENTE_DE_ONTEM = LocalDate.of(2026, 9, 25);

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
    private String token;

    @BeforeEach
    void umaLojaEUmaPessoaNovas() {
        mongo.getCollection("produtos").deleteMany(new Document());
        loja = UUID.randomUUID();
        UUID atendente = UUID.randomUUID();
        token = IDENTITY.tokenDe(atendente);
        MERCHANT.respondeCom(atendente, loja, "COLABORADOR", List.of("VER_PRODUTO"));
    }

    // ── 1 a 3 · o 200 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("1 · pizza com tamanho e borda, e refrigerante: preço unitário, subtotal, nomes, modo e marca")
    @SuppressWarnings("unchecked")
    void cota_e_precifica() {
        Pizza pizza = pizza(loja);
        Produto refri = refrigerante(loja);

        Map<String, Object> corpo = cotar(itens(
                item(pizza.produto.getId(), 2, pizza.grande, pizza.catupiry),
                item(refri.getId(), 1)))
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        List<Map<String, Object>> linhas = (List<Map<String, Object>>) corpo.get("linhas");
        assertThat(linhas).hasSize(2);
        Map<String, Object> l0 = linhas.get(0);
        assertThat(dinheiro(l0.get("precoUnitario"))).isEqualByComparingTo("63.00");
        assertThat(dinheiro(l0.get("subtotal"))).isEqualByComparingTo("126.00");
        assertThat(dinheiro(l0.get("precoBase"))).isEqualByComparingTo("40.00");
        assertThat(l0).containsEntry("nomeDoProduto", "Pizza de calabresa")
                .containsEntry("quantidade", 2)
                .containsEntry("modoDeControle", "QUALITATIVO");
        List<Map<String, Object>> opcoes = (List<Map<String, Object>>) l0.get("opcoes");
        assertThat(opcoes).extracting(o -> o.get("nomeDoGrupo")).containsExactly("Tamanho", "Borda");
        assertThat(opcoes).extracting(o -> o.get("nomeDaOpcao")).containsExactly("Grande", "Catupiry");
        assertThat(dinheiro(opcoes.get(0).get("acrescimo"))).isEqualByComparingTo("15.00");

        Map<String, Object> l1 = linhas.get(1);
        assertThat(dinheiro(l1.get("precoUnitario"))).isEqualByComparingTo("6.00");
        assertThat(dinheiro(l1.get("subtotal"))).isEqualByComparingTo("6.00");
        assertThat(l1).containsEntry("modoDeControle", "SEM_CONTROLE");

        assertThat(dinheiro(corpo.get("subtotalDosItens"))).isEqualByComparingTo("132.00");
        assertThat(corpo.get("cotadoEm")).isNotNull();
    }

    @Test
    @DisplayName("2 · acréscimo negativo baixa o preço unitário")
    @SuppressWarnings("unchecked")
    void acrescimo_negativo() {
        Produto p = Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA, "Lasanha",
                Precos.reais("30.00"), ModoDeControle.QUALITATIVO, 0);
        Opcao semQueijo = Opcao.nova("Sem queijo", Precos.reais("-2.00"), 0);
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Ajustes", 0, 1, 0, List.of(semQueijo)));
        p.publicar();
        p = produtos.salvar(p);

        Map<String, Object> corpo = cotar(itens(item(p.getId(), 1, semQueijo.id())))
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        Map<String, Object> linha = ((List<Map<String, Object>>) corpo.get("linhas")).get(0);
        assertThat(dinheiro(linha.get("precoUnitario"))).isEqualByComparingTo("28.00");
    }

    @Test
    @DisplayName("3 · o mesmo produto duas vezes são duas linhas, e as opções saem na ordem dos grupos")
    @SuppressWarnings("unchecked")
    void duas_linhas_e_a_ordem_dos_grupos() {
        Pizza pizza = pizza(loja);

        Map<String, Object> corpo = cotar(itens(
                item(pizza.produto.getId(), 1, pizza.catupiry, pizza.grande),
                item(pizza.produto.getId(), 1, pizza.pequena)))
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        List<Map<String, Object>> linhas = (List<Map<String, Object>>) corpo.get("linhas");
        assertThat(linhas).hasSize(2);
        List<Map<String, Object>> primeira = (List<Map<String, Object>>) linhas.get(0).get("opcoes");
        assertThat(primeira).extracting(o -> o.get("nomeDaOpcao")).containsExactly("Grande", "Catupiry");
        List<Map<String, Object>> segunda = (List<Map<String, Object>>) linhas.get(1).get("opcoes");
        assertThat(segunda).extracting(o -> o.get("nomeDaOpcao")).containsExactly("Pequena");
    }

    // ── 4 a 8 · o 400 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("4 · opção de outro produto é 400, e a frase não traz identificador")
    void opcao_de_outro_produto() {
        Pizza pizza = pizza(loja);
        Pizza outra = pizza(loja);

        String detail = detail(cotar(itens(item(pizza.produto.getId(), 1, outra.grande)))
                .expectStatus().isBadRequest());

        assertThat(detail).isNotBlank()
                .doesNotContain(outra.grande.toString())
                .doesNotContain(pizza.produto.getId().toString());
    }

    @Test
    @DisplayName("5 · grupo obrigatório sem escolha é 400, e a frase diz o grupo")
    void obrigatorio_sem_escolha() {
        Pizza pizza = pizza(loja);

        assertThat(detail(cotar(itens(item(pizza.produto.getId(), 1, pizza.catupiry)))
                .expectStatus().isBadRequest())).contains("Tamanho");
    }

    @Test
    @DisplayName("6 · grupo acima do máximo é 400, e a frase diz o grupo")
    void acima_do_maximo() {
        Pizza pizza = pizza(loja);

        assertThat(detail(cotar(itens(item(pizza.produto.getId(), 1,
                pizza.grande, pizza.catupiry, pizza.cheddar)))
                .expectStatus().isBadRequest())).contains("Borda");
    }

    @Test
    @DisplayName("7 · a mesma opção duas vezes é 400")
    void opcao_repetida() {
        Pizza pizza = pizza(loja);

        cotar(itens(item(pizza.produto.getId(), 1, pizza.grande, pizza.grande)))
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("8 · quantidade zero é 400, e lista vazia também")
    void quantidade_zero_e_lista_vazia() {
        Produto refri = refrigerante(loja);

        cotar(itens(item(refri.getId(), 0))).expectStatus().isBadRequest();
        cotar("{\"itens\":[]}").expectStatus().isBadRequest();
    }

    // ── 9 a 14 · o 409 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("9 · produto em rascunho é 409, com ele na lista, item 0 e sem opção")
    void rascunho() {
        Produto rascunho = produtos.salvar(Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA,
                "Pizza em teste", Precos.reais("39.90"), ModoDeControle.QUALITATIVO, 0));

        List<Map<String, Object>> lista = indisponiveis(cotar(itens(item(rascunho.getId(), 1))));

        assertThat(lista).singleElement().satisfies(e -> {
            assertThat(e).containsEntry("item", 0).containsEntry("produtoId", rascunho.getId().toString());
            assertThat(e.get("opcaoId")).isNull();
        });
    }

    @Test
    @DisplayName("10 · produto esgotado hoje é 409, com ele na lista, item 0 e sem opção")
    void esgotado_hoje() {
        Produto refri = Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA, "Suco",
                Precos.reais("8.00"), ModoDeControle.QUALITATIVO, 0);
        refri.publicar();
        refri.marcar(esgotado());
        Produto salvo = produtos.salvar(refri);

        List<Map<String, Object>> lista = indisponiveis(cotar(itens(item(salvo.getId(), 1))));

        assertThat(lista).singleElement().satisfies(e -> {
            assertThat(e).containsEntry("item", 0).containsEntry("produtoId", salvo.getId().toString());
            assertThat(e.get("opcaoId")).isNull();
        });
    }

    @Test
    @DisplayName("11 · produto de outra loja e produto inexistente: 409 nos dois, corpos iguais salvo o produtoId")
    @SuppressWarnings("unchecked")
    void outra_loja_e_inexistente() {
        Produto daOutra = refrigerante(UUID.randomUUID());
        UUID inexistente = UUID.randomUUID();

        Map<String, Object> deOutraLoja = cotar(itens(item(daOutra.getId(), 1)))
                .expectStatus().isEqualTo(409).expectBody(Map.class).returnResult().getResponseBody();
        Map<String, Object> naoExiste = cotar(itens(item(inexistente, 1)))
                .expectStatus().isEqualTo(409).expectBody(Map.class).returnResult().getResponseBody();

        List<Map<String, Object>> a = (List<Map<String, Object>>) deOutraLoja.get("indisponiveis");
        List<Map<String, Object>> b = (List<Map<String, Object>>) naoExiste.get("indisponiveis");
        assertThat(a).singleElement().satisfies(e -> assertThat(e).containsEntry("produtoId", daOutra.getId().toString()));
        assertThat(b).singleElement().satisfies(e -> assertThat(e).containsEntry("produtoId", inexistente.toString()));

        assertThat(semProdutoId(deOutraLoja)).isEqualTo(semProdutoId(naoExiste));
    }

    @Test
    @DisplayName("12 · uma opção escolhida esgotada é 409, com o produto e a opção")
    void uma_opcao_esgotada() {
        Pizza pizza = pizza(loja, false, true);

        List<Map<String, Object>> lista = indisponiveis(cotar(itens(
                item(pizza.produto.getId(), 1, pizza.grande, pizza.catupiry))));

        assertThat(lista).singleElement().satisfies(e -> assertThat(e)
                .containsEntry("item", 0)
                .containsEntry("produtoId", pizza.produto.getId().toString())
                .containsEntry("opcaoId", pizza.catupiry.toString()));
    }

    @Test
    @DisplayName("13 · duas opções escolhidas esgotadas no mesmo item: duas entradas, o mesmo item, as duas opções")
    void duas_opcoes_esgotadas() {
        Pizza pizza = pizza(loja, true, true);

        List<Map<String, Object>> lista = indisponiveis(cotar(itens(
                item(pizza.produto.getId(), 1, pizza.grande, pizza.catupiry))));

        assertThat(lista).hasSize(2);
        assertThat(lista).extracting(e -> e.get("item")).containsOnly(0);
        assertThat(lista).extracting(e -> e.get("produtoId")).containsOnly(pizza.produto.getId().toString());
        assertThat(lista).extracting(e -> e.get("opcaoId"))
                .containsExactly(pizza.grande.toString(), pizza.catupiry.toString());
    }

    @Test
    @DisplayName("14 · um bom, um esgotado, um com opção esgotada: duas entradas, item 1 e 2, e o bom não aparece")
    void tudo_o_que_caiu_de_uma_vez() {
        Produto bom = refrigerante(loja);
        Produto esgotado = Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA, "Suco",
                Precos.reais("8.00"), ModoDeControle.QUALITATIVO, 0);
        esgotado.publicar();
        esgotado.marcar(esgotado());
        esgotado = produtos.salvar(esgotado);
        Pizza pizza = pizza(loja, false, true);

        List<Map<String, Object>> lista = indisponiveis(cotar(itens(
                item(bom.getId(), 1),
                item(esgotado.getId(), 1),
                item(pizza.produto.getId(), 1, pizza.grande, pizza.catupiry))));

        assertThat(lista).hasSize(2);
        assertThat(lista).extracting(e -> e.get("item")).containsExactly(1, 2);
        assertThat(lista.get(0)).containsEntry("produtoId", esgotado.getId().toString());
        assertThat(lista.get(0).get("opcaoId")).isNull();
        assertThat(lista.get(1)).containsEntry("opcaoId", pizza.catupiry.toString());
        assertThat(lista).extracting(e -> e.get("produtoId")).doesNotContain(bom.getId().toString());
    }

    @Test
    @DisplayName("15 · um esgotado e um com opção de outro produto: 400 — o defeito vence o mundo")
    void o_400_vence_o_409() {
        Produto esgotado = Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA, "Suco",
                Precos.reais("8.00"), ModoDeControle.QUALITATIVO, 0);
        esgotado.publicar();
        esgotado.marcar(esgotado());
        esgotado = produtos.salvar(esgotado);
        Pizza pizza = pizza(loja);
        Pizza outra = pizza(loja);

        cotar(itens(item(esgotado.getId(), 1), item(pizza.produto.getId(), 1, outra.grande)))
                .expectStatus().isBadRequest();
    }

    // ── 16 a 18 · quem pode ─────────────────────────────────────────────────

    @Test
    @DisplayName("16 · vínculo ativo sem nenhuma permissão cota — 200")
    void vinculo_sem_permissao() {
        Produto refri = refrigerante(loja);
        UUID semNada = UUID.randomUUID();
        MERCHANT.respondeCom(semNada, loja, "COLABORADOR", List.of());
        token = IDENTITY.tokenDe(semNada);

        cotar(itens(item(refri.getId(), 1))).expectStatus().isOk();
    }

    @Test
    @DisplayName("17 · sem vínculo, e com o merchant fora do ar: 403 nos dois, com o mesmo corpo")
    void sem_vinculo_e_merchant_fora() {
        Produto refri = refrigerante(loja);
        MERCHANT.nega();
        String negado = detail(cotar(itens(item(refri.getId(), 1))).expectStatus().isForbidden());

        loja = UUID.randomUUID();
        token = IDENTITY.tokenDe(UUID.randomUUID());
        MERCHANT.estoura();
        String foraDoAr = detail(cotar(itens(item(refri.getId(), 1))).expectStatus().isForbidden());

        assertThat(negado).isNotBlank().isEqualTo(foraDoAr);
    }

    @Test
    @DisplayName("18 · sem token, 401")
    void sem_token() {
        cliente().post().uri("/internal/merchants/" + loja + "/catalog/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .body(itens(item(UUID.randomUUID(), 1)))
                .exchange().expectStatus().isUnauthorized();
    }

    // ── 19 · sem cache ──────────────────────────────────────────────────────

    @Test
    @DisplayName("19 · cota, marca o produto esgotado, cota de novo: 200 e depois 409 — sem cache (ADR-034)")
    void sem_cache() {
        Produto suco = Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA, "Suco",
                Precos.reais("8.00"), ModoDeControle.QUALITATIVO, 0);
        suco.publicar();
        suco = produtos.salvar(suco);

        cotar(itens(item(suco.getId(), 1))).expectStatus().isOk();

        Produto relido = produtos.buscarPorId(suco.getId()).orElseThrow();
        relido.marcar(esgotado());
        produtos.salvar(relido);

        cotar(itens(item(suco.getId(), 1))).expectStatus().isEqualTo(409);
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    /** Pizza de calabresa, base 40: Tamanho (1 a 1: Pequena 0, Grande +15) e Borda (0 a 1: Catupiry +8, Cheddar +6). */
    private record Pizza(Produto produto, UUID pequena, UUID grande, UUID catupiry, UUID cheddar) {
    }

    private Pizza pizza(UUID daLoja) {
        return pizza(daLoja, false, false);
    }

    /** A pizza, com a Grande e a Catupiry esgotadas ou não; cada uma num grupo que ainda tem outra disponível. */
    private Pizza pizza(UUID daLoja, boolean grandeEsgotada, boolean catupiryEsgotada) {
        Opcao pequena = Opcao.nova("Pequena", Precos.reais("0.00"), 0);
        Opcao grande = Opcao.nova("Grande", Precos.reais("15.00"), 1);
        Opcao catupiry = Opcao.nova("Catupiry", Precos.reais("8.00"), 0);
        Opcao cheddar = Opcao.nova("Cheddar", Precos.reais("6.00"), 1);
        if (grandeEsgotada) {
            grande = grande.com(esgotado());
        }
        if (catupiryEsgotada) {
            catupiry = catupiry.com(esgotado());
        }
        Produto p = Produto.rascunho(daLoja, ProdutoDeTeste.CATEGORIA, "Pizza de calabresa",
                Precos.reais("40.00"), ModoDeControle.QUALITATIVO, 0);
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Tamanho", 1, 1, 0, List.of(pequena, grande)));
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Borda", 0, 1, 1, List.of(catupiry, cheddar)));
        p.publicar();
        return new Pizza(produtos.salvar(p), pequena.id(), grande.id(), catupiry.id(), cheddar.id());
    }

    private Produto refrigerante(UUID daLoja) {
        Produto p = Produto.rascunho(daLoja, ProdutoDeTeste.CATEGORIA, "Refrigerante lata",
                Precos.reais("6.00"), ModoDeControle.SEM_CONTROLE, 1);
        p.publicar();
        return produtos.salvar(p);
    }

    private static Disponibilidade esgotado() {
        return Disponibilidade.esgotadoHoje(ONTEM_A_NOITE, EXPEDIENTE_DE_ONTEM);
    }

    private static String item(UUID produtoId, int quantidade, UUID... opcoes) {
        String lista = java.util.Arrays.stream(opcoes).map(o -> "\"" + o + "\"")
                .collect(Collectors.joining(","));
        return "{\"produtoId\":\"" + produtoId + "\",\"quantidade\":" + quantidade
                + ",\"opcoes\":[" + lista + "]}";
    }

    private static String itens(String... itens) {
        return "{\"itens\":[" + String.join(",", itens) + "]}";
    }

    private RestTestClient cliente() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    private RestTestClient.ResponseSpec cotar(String corpo) {
        return cliente().post().uri("/internal/merchants/" + loja + "/catalog/quote")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .exchange();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> indisponiveis(RestTestClient.ResponseSpec resposta) {
        Map<String, Object> corpo = resposta.expectStatus().isEqualTo(409)
                .expectBody(Map.class).returnResult().getResponseBody();
        return (List<Map<String, Object>>) corpo.get("indisponiveis");
    }

    @SuppressWarnings("unchecked")
    private static String detail(RestTestClient.ResponseSpec status) {
        Map<String, Object> corpo = status.expectBody(Map.class).returnResult().getResponseBody();
        return (String) corpo.get("detail");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> semProdutoId(Map<String, Object> corpo) {
        Map<String, Object> copia = new java.util.HashMap<>(corpo);
        copia.remove("instance");
        List<Map<String, Object>> lista = ((List<Map<String, Object>>) corpo.get("indisponiveis")).stream()
                .map(e -> {
                    Map<String, Object> m = new java.util.HashMap<>(e);
                    m.remove("produtoId");
                    return m;
                }).toList();
        copia.put("indisponiveis", lista);
        return copia;
    }

    private static BigDecimal dinheiro(Object valor) {
        return new BigDecimal(valor.toString());
    }
}
