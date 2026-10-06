package com.deliveryplatform.catalog.integration;

import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.IdentityDeMentira;
import com.deliveryplatform.catalog.support.Infraestrutura;
import com.deliveryplatform.catalog.support.MerchantDeMentira;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O contrato congelado declara as recusas que cada rota devolve — e só elas
 * (ADR-053).
 *
 * <h2>As duas direções, e por que não há ordem entre casos</h2>
 *
 * <p>As provocações moram numa lista só, {@link #provocacoes()}: rota, código
 * esperado e como provocá-lo por HTTP. Dela saem as duas metades:
 *
 * <ul>
 *   <li><b>o que se observa está declarado</b> — {@link #cada_recusa_provocada_esta_no_contrato},
 *       um teste dinâmico por provocação: chama, confere o código e confere que a
 *       operação do contrato o declara;</li>
 *   <li><b>o que se declara foi provocado</b> — {@link #nada_declarado_sem_provocacao}:
 *       todo código de erro do contrato está na lista, ou em {@link #NAO_PROVOCAVEIS}
 *       com o motivo escrito.</li>
 * </ul>
 *
 * <p>A segunda metade lê a lista, não um registro do que a primeira observou —
 * e por isso nenhum caso depende de outro ter rodado antes. Que cada item da lista
 * produz mesmo o código que diz é o trabalho da primeira metade.
 *
 * <h2>Onde está o contrato</h2>
 *
 * <p>Pelo mesmo caminho do {@code ContratoOpenApiIT}: sobe do diretório de
 * trabalho até achar {@code contracts/}. Não há ajudante compartilhado; a busca
 * está copiada, e o {@code ContratoOpenApiIT} é a referência.
 *
 * <h2>O que este teste NÃO prova</h2>
 *
 * <p><b>Nada sobre o corpo.</b> O esquema do {@code ProblemDetail}, o
 * {@code detail}, se a mensagem vaza algo de dentro — nada disso é conferido, e o
 * contrato não declara corpo de erro nenhum. O 401 nem tem corpo: sai da cadeia de
 * segurança. <b>Gatilho escrito:</b> a primeira tela que mostre {@code detail} ao
 * usuário — a W-C.
 *
 * <p><b>Nem os códigos de protocolo</b> — 405, 406, 415 —, que toda rota devolve
 * por ser HTTP e não por ser esta rota. O contrato não os declara (ADR-053).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContratoDeErrosIT extends Infraestrutura {

    private static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();
    private static final MerchantDeMentira MERCHANT = MerchantDeMentira.subir();

    private static final String BASE = "/api/v1/merchants/{estabelecimentoId}/catalog/produtos";
    private static final String LISTAR = BASE;
    private static final String MARCAR_PRODUTO = BASE + "/{produtoId}/disponibilidade";
    private static final String MARCAR_OPCAO =
            BASE + "/{produtoId}/grupos/{grupoId}/opcoes/{opcaoId}/disponibilidade";

    /**
     * O que o contrato declara e nenhum caso provoca, cada item com o motivo.
     * Chave: {@code "METODO caminho código"}.
     */
    private static final Map<String, String> NAO_PROVOCAVEIS = Map.of();

    /*
     * E um que NÃO está no mapa, porque o código já está coberto — escrito aqui
     * para que ninguém leia a lista de provocações e conclua que ele foi testado:
     *
     * PUT …/disponibilidade (as duas) · 409 por conflito de versão (ADR-052).
     * Provocar exigiria uma costura no código de produção para alterar o documento
     * entre a leitura e a gravação do controlador. O 409 declarado é provado pela
     * loja sem horário. Gatilho escrito: o primeiro relato de 409 que ninguém
     * consegue explicar.
     */

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

    @Autowired
    ProdutoRepositorio produtos;

    // ── as provocações ──────────────────────────────────────────────────────

    record Provocacao(String metodo, String caminho, int codigo, String como, IntSupplier chamada) {
        String chave() {
            return metodo + " " + caminho + " " + codigo;
        }
    }

    private List<Provocacao> provocacoes() {
        return List.of(
                new Provocacao("GET", LISTAR, 400, "identificador da loja que não é UUID",
                        () -> listar("nao-e-uuid", comPermissoes(UUID.randomUUID(), "VER_PRODUTO"))),
                new Provocacao("GET", LISTAR, 401, "sem token",
                        () -> listar(UUID.randomUUID().toString(), null)),
                new Provocacao("GET", LISTAR, 403, "o merchant nega o vínculo",
                        () -> {
                            UUID loja = UUID.randomUUID();
                            String token = IDENTITY.tokenDe(UUID.randomUUID());
                            MERCHANT.nega();
                            return listar(loja.toString(), token);
                        }),

                new Provocacao("PUT", MARCAR_PRODUTO, 400,
                        "produto SEM_CONTROLE marcado como esgotado — RegraDoCatalogoViolada",
                        () -> {
                            Cena c = cena("ALTERAR_PRODUTO");
                            Produto lata = produtos.salvar(Produto.rascunho(c.loja,
                                    ProdutoDeTeste.CATEGORIA, "Lata", Precos.reais("7.00"),
                                    ModoDeControle.SEM_CONTROLE, 1));
                            return marcarProduto(c, lata.getId(), "ESGOTADO_INDETERMINADO");
                        }),
                new Provocacao("PUT", MARCAR_PRODUTO, 401, "sem token",
                        () -> {
                            Cena c = cena("ALTERAR_PRODUTO");
                            return marcarProduto(c.semToken(), c.pizza.getId(), "ACABANDO");
                        }),
                new Provocacao("PUT", MARCAR_PRODUTO, 403, "vínculo sem ALTERAR_PRODUTO",
                        () -> {
                            Cena c = cena("VER_PRODUTO");
                            return marcarProduto(c, c.pizza.getId(), "ACABANDO");
                        }),
                new Provocacao("PUT", MARCAR_PRODUTO, 409, "ESGOTADO_HOJE numa loja sem horário",
                        () -> {
                            Cena c = cena("ALTERAR_PRODUTO");
                            MERCHANT.semHorario();
                            return marcarProduto(c, c.pizza.getId(), "ESGOTADO_HOJE");
                        }),
                new Provocacao("PUT", MARCAR_PRODUTO, 503, "o expediente do merchant fora do ar",
                        () -> {
                            Cena c = cena("ALTERAR_PRODUTO");
                            MERCHANT.expedienteEstoura();
                            return marcarProduto(c, c.pizza.getId(), "ACABANDO");
                        }),

                new Provocacao("PUT", MARCAR_OPCAO, 400,
                        "opção que não é deste produto — RegraDoCatalogoViolada",
                        () -> {
                            Cena c = cena("ALTERAR_PRODUTO");
                            return marcarOpcao(c, c.tamanho().id(), UUID.randomUUID(), "ACABANDO");
                        }),
                new Provocacao("PUT", MARCAR_OPCAO, 401, "sem token",
                        () -> {
                            Cena c = cena("ALTERAR_PRODUTO");
                            return marcarOpcao(c.semToken(), c.tamanho().id(), c.pequena(), "ACABANDO");
                        }),
                new Provocacao("PUT", MARCAR_OPCAO, 403, "vínculo sem ALTERAR_PRODUTO",
                        () -> {
                            Cena c = cena("VER_PRODUTO");
                            return marcarOpcao(c, c.tamanho().id(), c.pequena(), "ACABANDO");
                        }),
                new Provocacao("PUT", MARCAR_OPCAO, 409, "ESGOTADO_HOJE numa loja sem horário",
                        () -> {
                            Cena c = cena("ALTERAR_PRODUTO");
                            MERCHANT.semHorario();
                            return marcarOpcao(c, c.tamanho().id(), c.pequena(), "ESGOTADO_HOJE");
                        }),
                new Provocacao("PUT", MARCAR_OPCAO, 503, "o expediente do merchant fora do ar",
                        () -> {
                            Cena c = cena("ALTERAR_PRODUTO");
                            MERCHANT.expedienteEstoura();
                            return marcarOpcao(c, c.tamanho().id(), c.pequena(), "ACABANDO");
                        }));
    }

    // ── as duas direções ────────────────────────────────────────────────────

    @TestFactory
    @DisplayName("cada recusa provocada por HTTP está declarada na operação do contrato")
    Stream<DynamicTest> cada_recusa_provocada_esta_no_contrato() {
        return provocacoes().stream().map(p -> DynamicTest.dynamicTest(
                p.chave() + " — " + p.como(),
                () -> {
                    assertThat(p.chamada().getAsInt())
                            .as("a rota devolveu outro código para: %s", p.como())
                            .isEqualTo(p.codigo());
                    assertThat(codigosDeclarados(p.metodo(), p.caminho()))
                            .as("o contrato não declara %s em %s %s", p.codigo(), p.metodo(), p.caminho())
                            .contains(String.valueOf(p.codigo()));
                }));
    }

    @Test
    @DisplayName("todo código de erro do contrato tem provocação — o contrato não incha")
    void nada_declarado_sem_provocacao() throws Exception {
        Set<String> provocados = new TreeSet<>();
        provocacoes().forEach(p -> provocados.add(p.chave()));

        Set<String> declarados = new TreeSet<>();
        JsonNode caminhos = contrato().path("paths");
        for (Iterator<Map.Entry<String, JsonNode>> it = caminhos.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> caminho = it.next();
            for (Iterator<Map.Entry<String, JsonNode>> ops = caminho.getValue().fields(); ops.hasNext(); ) {
                Map.Entry<String, JsonNode> op = ops.next();
                op.getValue().path("responses").fieldNames().forEachRemaining(codigo -> {
                    if (!codigo.startsWith("2")) {
                        declarados.add(op.getKey().toUpperCase() + " " + caminho.getKey() + " " + codigo);
                    }
                });
            }
        }

        declarados.removeAll(NAO_PROVOCAVEIS.keySet());
        assertThat(provocados)
                .as("declarado no contrato sem nenhuma provocação que o produza")
                .containsAll(declarados);
    }

    /**
     * Resposta de erro sem corpo declarado. Sem {@code content = @Content} no
     * {@code @ApiResponse}, o springdoc copia o tipo de retorno do método para
     * ela — e o contrato passa a dizer que o 403 devolve um produto. Foi o que a
     * G-E produziu na primeira regravação, e os tipos do front o copiaram.
     */
    @Test
    @DisplayName("nenhuma resposta de erro declara o corpo do 200")
    void erro_nao_declara_corpo() throws Exception {
        Set<String> comCorpo = new TreeSet<>();
        JsonNode caminhos = contrato().path("paths");
        for (Iterator<Map.Entry<String, JsonNode>> it = caminhos.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> caminho = it.next();
            for (Iterator<Map.Entry<String, JsonNode>> ops = caminho.getValue().fields(); ops.hasNext(); ) {
                Map.Entry<String, JsonNode> op = ops.next();
                op.getValue().path("responses").fields().forEachRemaining(r -> {
                    if (!r.getKey().startsWith("2") && r.getValue().has("content")) {
                        comCorpo.add(op.getKey().toUpperCase() + " " + caminho.getKey() + " " + r.getKey());
                    }
                });
            }
        }
        assertThat(comCorpo).as("erro com corpo declarado — o corpo não é afirmado (ADR-053)").isEmpty();
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    /** Uma loja nova, uma pessoa com as permissões dadas, e uma pizza com "Tamanho". */
    private Cena cena(String... permissoes) {
        UUID loja = UUID.randomUUID();
        UUID pessoa = UUID.randomUUID();
        MERCHANT.respondeCom(pessoa, loja, "ADMINISTRADOR", List.of(permissoes));
        MERCHANT.expedienteE(java.time.LocalDate.parse("2026-10-06"));
        Produto pizza = Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA, "Pizza margherita",
                Precos.reais("49.90"), ModoDeControle.QUALITATIVO, 0);
        pizza.acrescentarGrupo(ProdutoDeTeste.tamanho(3));
        pizza.publicar();
        return new Cena(loja, IDENTITY.tokenDe(pessoa), produtos.salvar(pizza));
    }

    private String comPermissoes(UUID loja, String... permissoes) {
        UUID pessoa = UUID.randomUUID();
        MERCHANT.respondeCom(pessoa, loja, "ADMINISTRADOR", List.of(permissoes));
        return IDENTITY.tokenDe(pessoa);
    }

    record Cena(UUID loja, String token, Produto pizza) {
        Cena semToken() {
            return new Cena(loja, null, pizza);
        }

        GrupoDeOpcoes tamanho() {
            return pizza.getGruposDeOpcoes().get(0);
        }

        UUID pequena() {
            return tamanho().opcoes().get(0).id();
        }
    }

    private RestTestClient cliente() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    private int listar(String loja, String token) {
        var requisicao = cliente().get().uri("/api/v1/merchants/" + loja + "/catalog/produtos");
        if (token != null) {
            requisicao = requisicao.header("Authorization", "Bearer " + token);
        }
        return requisicao.exchange().returnResult(String.class).getStatus().value();
    }

    private int marcarProduto(Cena c, UUID produtoId, String estado) {
        return put("/api/v1/merchants/" + c.loja() + "/catalog/produtos/" + produtoId
                + "/disponibilidade", c.token(), estado);
    }

    private int marcarOpcao(Cena c, UUID grupoId, UUID opcaoId, String estado) {
        return put("/api/v1/merchants/" + c.loja() + "/catalog/produtos/" + c.pizza().getId()
                + "/grupos/" + grupoId + "/opcoes/" + opcaoId + "/disponibilidade", c.token(), estado);
    }

    private int put(String uri, String token, String estado) {
        var requisicao = cliente().put().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"estado\":\"" + estado + "\"}");
        if (token != null) {
            requisicao = requisicao.header("Authorization", "Bearer " + token);
        }
        return requisicao.exchange().returnResult(String.class).getStatus().value();
    }

    private static Set<String> codigosDeclarados(String metodo, String caminho) throws Exception {
        Set<String> codigos = new TreeSet<>();
        contrato().path("paths").path(caminho).path(metodo.toLowerCase())
                .path("responses").fieldNames().forEachRemaining(codigos::add);
        return codigos;
    }

    private static JsonNode contrato() throws Exception {
        return new ObjectMapper().readTree(Files.readString(arquivoDoContrato()));
    }

    /** A mesma busca do {@code ContratoOpenApiIT}. */
    private static Path arquivoDoContrato() {
        Path atual = Path.of("").toAbsolutePath();
        while (atual != null && !Files.isDirectory(atual.resolve("contracts"))) {
            atual = atual.getParent();
        }
        if (atual == null) {
            throw new IllegalStateException(
                    "não encontrei o diretório contracts/ subindo a partir de "
                            + Path.of("").toAbsolutePath());
        }
        return atual.resolve("contracts").resolve("openapi").resolve("catalog-service.json");
    }
}
