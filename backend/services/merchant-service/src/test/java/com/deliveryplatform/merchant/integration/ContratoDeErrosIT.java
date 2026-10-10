package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.support.IdentityDeMentira;
import com.deliveryplatform.merchant.support.Infraestrutura;
import com.deliveryplatform.merchant.support.LojaDeTeste;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
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
 * O contrato congelado do {@code merchant} declara as recusas que cada rota
 * devolve — e só elas (ADR-053). A mesma forma do {@code ContratoDeErrosIT} do
 * {@code catalog}: uma lista de provocações, e dela as duas direções.
 *
 * <p>Nada sobre o corpo, e nada sobre códigos de protocolo (405, 406, 415) —
 * ver o do {@code catalog}.
 *
 * <p><b>Um 403 que o código tem e o contrato não declara</b>, e é de propósito:
 * {@code GET /api/v1/me/estabelecimentos} recusa com 403 um token cujo
 * {@code sub} não é UUID ({@code SujeitoDoToken}). O {@code identity} só emite
 * {@code sub} UUID, então o único jeito de chegar lá é um token forjado com a
 * chave verdadeira. Declarar 403 em "as minhas lojas" faria o cliente esperar
 * uma recusa que o sistema não produz.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "delivery.outbox.habilitado=false")
class ContratoDeErrosIT extends Infraestrutura {

    private static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();

    private static final String CONTEXTO = "/internal/merchants/{estabelecimentoId}/me/contexto-de-acesso";
    private static final String EXPEDIENTE = "/internal/merchants/{estabelecimentoId}/expediente-corrente";
    private static final String EQUIPE = "/api/v1/merchants/{estabelecimentoId}/team";
    private static final String MINHAS_LOJAS = "/api/v1/me/estabelecimentos";

    /** O que o contrato declara e nenhum caso provoca. Hoje: nada. */
    private static final Map<String, String> NAO_PROVOCAVEIS = Map.of();

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

    // ── as provocações ──────────────────────────────────────────────────────

    record Provocacao(String metodo, String caminho, int codigo, String como, IntSupplier chamada) {
        String chave() {
            return metodo + " " + caminho + " " + codigo;
        }
    }

    private List<Provocacao> provocacoes() {
        return List.of(
                new Provocacao("GET", CONTEXTO, 400, "identificador da loja que não é UUID",
                        () -> get("/internal/merchants/nao-e-uuid/me/contexto-de-acesso", estranho())),
                new Provocacao("GET", CONTEXTO, 401, "sem token",
                        () -> get("/internal/merchants/" + UUID.randomUUID() + "/me/contexto-de-acesso", null)),
                new Provocacao("GET", CONTEXTO, 403, "sem vínculo com a loja",
                        () -> get("/internal/merchants/" + loja() + "/me/contexto-de-acesso", estranho())),

                new Provocacao("GET", EXPEDIENTE, 400, "identificador da loja que não é UUID",
                        () -> get("/internal/merchants/nao-e-uuid/expediente-corrente", estranho())),
                new Provocacao("GET", EXPEDIENTE, 401, "sem token",
                        () -> get("/internal/merchants/" + UUID.randomUUID() + "/expediente-corrente", null)),
                new Provocacao("GET", EXPEDIENTE, 403, "sem vínculo com a loja",
                        () -> get("/internal/merchants/" + loja() + "/expediente-corrente", estranho())),
                new Provocacao("GET", EXPEDIENTE, 409, "loja que não abre por horário (ADR-049 §5)",
                        () -> {
                            UUID semHorario = lojas.salvar(LojaDeTeste.semHorario()).getId();
                            String token = IDENTITY.tokenDe(vinculado(semHorario, Permissao.VER_PRODUTO));
                            return get("/internal/merchants/" + semHorario + "/expediente-corrente", token);
                        }),

                new Provocacao("GET", EQUIPE, 400, "identificador da loja que não é UUID",
                        () -> get("/api/v1/merchants/nao-e-uuid/team", estranho())),
                new Provocacao("GET", EQUIPE, 401, "sem token",
                        () -> get("/api/v1/merchants/" + UUID.randomUUID() + "/team", null)),
                new Provocacao("GET", EQUIPE, 403, "vínculo sem GERENCIAR_EQUIPE",
                        () -> {
                            UUID loja = loja();
                            String token = IDENTITY.tokenDe(vinculado(loja, Permissao.VER_PRODUTO));
                            return get("/api/v1/merchants/" + loja + "/team", token);
                        }),

                new Provocacao("GET", MINHAS_LOJAS, 401, "sem token",
                        () -> get("/api/v1/me/estabelecimentos", null)),

                // A primeira escrita do merchant (ADR-060): 201, 400 e 401, e mais nada.
                new Provocacao("POST", MINHAS_LOJAS, 400, "documento que o agregado recusa",
                        () -> post("/api/v1/me/estabelecimentos",
                                CriarEstabelecimentoIT.CORPO.formatted("Loja", "123"), estranho())),
                new Provocacao("POST", MINHAS_LOJAS, 401, "sem token",
                        () -> post("/api/v1/me/estabelecimentos",
                                CriarEstabelecimentoIT.CORPO.formatted("Loja", "123.456.789-09"), null)));
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

    private UUID loja() {
        return lojas.salvar(LojaDeTeste.pizzaria()).getId();
    }

    /** Token válido de alguém sem vínculo com loja nenhuma. */
    private String estranho() {
        return IDENTITY.tokenDe(UUID.randomUUID());
    }

    private UUID vinculado(UUID loja, Permissao permissao) {
        UUID pessoa = UUID.randomUUID();
        Instant agora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        membros.salvar(Membro.reconstituir(UUID.randomUUID(), pessoa, loja,
                Papel.COLABORADOR, EnumSet.of(permissao), EstadoDoMembro.ATIVO, agora, agora));
        return pessoa;
    }

    private int get(String uri, String token) {
        var requisicao = RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build()
                .get().uri(uri);
        if (token != null) {
            requisicao = requisicao.header("Authorization", "Bearer " + token);
        }
        return requisicao.exchange().returnResult(String.class).getStatus().value();
    }

    private int post(String uri, String corpo, String token) {
        var requisicao = RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build()
                .post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo);
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
        return atual.resolve("contracts").resolve("openapi").resolve("merchant-service.json");
    }
}
