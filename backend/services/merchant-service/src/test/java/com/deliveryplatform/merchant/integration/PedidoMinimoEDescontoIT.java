package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Disponibilidade;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Operacao;
import com.deliveryplatform.merchant.domain.model.TipoDeOperacao;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /settings/pedido-minimo} e {@code /settings/desconto-retirada} (ADR-080), de ponta a
 * ponta no {@code merchant}: gravação pelo repositório de verdade, leitura de volta, as recusas
 * do domínio com a frase do tratador, e a autorização no molde do horário.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "delivery.outbox.habilitado=false",
        "delivery.expediente.varredura-habilitada=false"})
class PedidoMinimoEDescontoIT extends Infraestrutura {

    private static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();

    static final String MINIMOS = """
            {"pedidoMinimoPorModalidade":{"ENTREGA":30.00,"RETIRADA":12.50}}""";
    static final String DESCONTO = """
            {"descontoDeRetirada":7.50}""";

    private static final String FRASE_DO_MINIMO = "informe um pedido mínimo para cada modalidade "
            + "que a loja aceita, e só para elas — zero ou mais, e zero é sem mínimo";

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

    /** A pizzaria do teste: entrega com mínimo de 25, retirada sem mínimo, desconto de 5. */
    @BeforeEach
    void umaPizzaria() {
        loja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        marli = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora())).getUsuarioId();
        bia = membros.salvar(Membro.colaborador(
                UUID.randomUUID(), loja, EquipeDeTeste.DA_BIA, agora())).getUsuarioId();
    }

    // ── 1 e 2 · o pedido mínimo ─────────────────────────────────────────────

    @Test
    @DisplayName("1 · grava o pedido mínimo pelo repositório de verdade, e o GET lê de volta")
    void grava_e_le_o_minimo() {
        put(PEDIDO_MINIMO, loja, marli, MINIMOS).expectStatus().isOk();

        Operacao gravada = lojas.buscarPorId(loja).orElseThrow().getOperacao();
        assertThat(gravada.pedidoMinimoDe(Modalidade.ENTREGA)).isEqualTo(Money.de("30.00"));
        assertThat(gravada.pedidoMinimoDe(Modalidade.RETIRADA)).isEqualTo(Money.de("12.50"));
        assertThat(gravada.descontoDeRetirada()).as("o desconto não muda").isEqualTo(Money.de("5.00"));

        Map<String, Object> lido = get(PEDIDO_MINIMO, loja, marli)
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(valores(lido, "pedidoMinimoPorModalidade"))
                .containsOnlyKeys("ENTREGA", "RETIRADA");
        assertThat(new BigDecimal(valores(lido, "pedidoMinimoPorModalidade").get("ENTREGA").toString()))
                .isEqualByComparingTo("30.00");
    }

    /**
     * A H1.2 com as palavras dela: "com zero valendo como 'sem mínimo'". O zero é gravado como
     * entrada e lido de volta; a modalidade omitida não vira zero — é recusada.
     */
    @Test
    @DisplayName("2 · zero vale como \"sem mínimo\": entra, fica, volta na leitura — e omitir não é zero")
    void zero_vale_como_sem_minimo() {
        put(PEDIDO_MINIMO, loja, marli, """
                {"pedidoMinimoPorModalidade":{"ENTREGA":0,"RETIRADA":0}}""")
                .expectStatus().isOk();

        Operacao gravada = lojas.buscarPorId(loja).orElseThrow().getOperacao();
        assertThat(gravada.pedidoMinimoPorModalidade())
                .containsOnlyKeys(Modalidade.ENTREGA, Modalidade.RETIRADA);
        assertThat(gravada.pedidoMinimoDe(Modalidade.ENTREGA)).isEqualTo(Money.ZERO);

        Map<String, Object> lido = get(PEDIDO_MINIMO, loja, marli)
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(valores(lido, "pedidoMinimoPorModalidade")).containsOnlyKeys("ENTREGA", "RETIRADA");

        ProblemDetail omitida = put(PEDIDO_MINIMO, loja, marli, """
                {"pedidoMinimoPorModalidade":{"ENTREGA":0}}""")
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();
        assertThat(omitida.getDetail()).isEqualTo(FRASE_DO_MINIMO);
    }

    // ── 3 · o desconto de retirada ──────────────────────────────────────────

    @Test
    @DisplayName("3 · grava o desconto de retirada, o GET lê de volta, e zero é aceito")
    void grava_e_le_o_desconto() {
        put(DESCONTO_RETIRADA, loja, marli, DESCONTO).expectStatus().isOk();

        Operacao gravada = lojas.buscarPorId(loja).orElseThrow().getOperacao();
        assertThat(gravada.descontoDeRetirada()).isEqualTo(Money.de("7.50"));
        assertThat(gravada.pedidoMinimoDe(Modalidade.ENTREGA)).as("o mínimo não muda")
                .isEqualTo(Money.de("25.00"));

        Map<String, Object> lido = get(DESCONTO_RETIRADA, loja, marli)
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(new BigDecimal(lido.get("descontoDeRetirada").toString())).isEqualByComparingTo("7.50");

        put(DESCONTO_RETIRADA, loja, marli, "{\"descontoDeRetirada\":0}").expectStatus().isOk();
        assertThat(lojas.buscarPorId(loja).orElseThrow().getOperacao().descontoDeRetirada())
                .isEqualTo(Money.ZERO);
    }

    // ── 4 · as recusas, que são do domínio ──────────────────────────────────

    @Test
    @DisplayName("4 · mínimo negativo e desconto negativo: 400 com a frase do tratador, e nada gravado")
    void negativos_sao_400() {
        ProblemDetail minimo = put(PEDIDO_MINIMO, loja, marli, """
                {"pedidoMinimoPorModalidade":{"ENTREGA":-1,"RETIRADA":0}}""")
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();
        assertThat(minimo.getDetail()).isEqualTo(FRASE_DO_MINIMO);

        ProblemDetail desconto = put(DESCONTO_RETIRADA, loja, marli, "{\"descontoDeRetirada\":-2}")
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();
        assertThat(desconto.getDetail()).isEqualTo("o desconto de retirada não pode ser negativo");

        Operacao intacta = lojas.buscarPorId(loja).orElseThrow().getOperacao();
        assertThat(intacta).isEqualTo(LojaDeTeste.operacao());
    }

    @Test
    @DisplayName("4b · mínimo para modalidade que a loja não aceita: 400, e a loja continua só entregando")
    void modalidade_nao_aceita_e_400() {
        UUID soEntrega = lojas.salvar(Estabelecimento.novo(
                LojaDeTeste.identificacao(FusoHorario.PADRAO),
                Operacao.nova(TipoDeOperacao.PRODUCAO,
                        Map.of(Modalidade.ENTREGA, Set.of(MetodoPagamento.DINHEIRO))),
                LojaDeTeste.troco(), Disponibilidade.semHorario(), LojaDeTeste.areas())).getId();
        UUID dona = membros.salvar(Membro.fundador(UUID.randomUUID(), soEntrega, agora())).getUsuarioId();

        ProblemDetail recusa = put(PEDIDO_MINIMO, soEntrega, dona, MINIMOS)
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();
        assertThat(recusa.getDetail()).isEqualTo(FRASE_DO_MINIMO);
        assertThat(lojas.buscarPorId(soEntrega).orElseThrow().getOperacao().pedidoMinimoPorModalidade())
                .containsOnlyKeys(Modalidade.ENTREGA);
    }

    @Test
    @DisplayName("4c · modalidade desconhecida falha na leitura do corpo, com 400")
    void modalidade_desconhecida_e_400() {
        put(PEDIDO_MINIMO, loja, marli, """
                {"pedidoMinimoPorModalidade":{"ENTREGA":10,"RETIRADA":0,"DRONE":5}}""")
                .expectStatus().isBadRequest();
        assertThat(lojas.buscarPorId(loja).orElseThrow().getOperacao()).isEqualTo(LojaDeTeste.operacao());
    }

    // ── 5 e 6 · a autorização ───────────────────────────────────────────────

    @Test
    @DisplayName("5 · sem token é 401; com token e sem CONFIGURAR_LOJA é 403 — nas quatro operações")
    void sem_token_e_sem_permissao() {
        for (String caminho : new String[] {PEDIDO_MINIMO, DESCONTO_RETIRADA}) {
            String corpo = caminho.equals(PEDIDO_MINIMO) ? MINIMOS : DESCONTO;
            cliente().get().uri(caminho.formatted(loja)).exchange().expectStatus().isUnauthorized();
            cliente().put().uri(caminho.formatted(loja))
                    .contentType(MediaType.APPLICATION_JSON).body(corpo)
                    .exchange().expectStatus().isUnauthorized();

            get(caminho, loja, bia).expectStatus().isForbidden();
            ProblemDetail recusa = put(caminho, loja, bia, corpo)
                    .expectStatus().isForbidden()
                    .expectBody(ProblemDetail.class).returnResult().getResponseBody();
            assertThat(recusa.getDetail()).isEqualTo("sem acesso a este estabelecimento");
        }
        assertThat(lojas.buscarPorId(loja).orElseThrow().getOperacao()).isEqualTo(LojaDeTeste.operacao());
    }

    /** O precedente é o caso 12 do {@code DefinirHorarioIT}: o 403 de M7, idêntico a "não existe". */
    @Test
    @DisplayName("6 · loja de outra pessoa dá o mesmo 403 que loja inexistente — corpo e status")
    void outra_loja_e_igual_a_inexistente() {
        UUID vizinha = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        membros.salvar(Membro.fundador(UUID.randomUUID(), vizinha, agora()));

        for (String caminho : new String[] {PEDIDO_MINIMO, DESCONTO_RETIRADA}) {
            String corpo = caminho.equals(PEDIDO_MINIMO) ? MINIMOS : DESCONTO;
            String deOutra = put(caminho, vizinha, marli, corpo)
                    .expectStatus().isForbidden()
                    .expectBody(String.class).returnResult().getResponseBody();
            String inexistente = put(caminho, UUID.randomUUID(), marli, corpo)
                    .expectStatus().isForbidden()
                    .expectBody(String.class).returnResult().getResponseBody();
            assertThat(semInstance(deOutra)).isEqualTo(semInstance(inexistente));
        }
        assertThat(lojas.buscarPorId(vizinha).orElseThrow().getOperacao()).isEqualTo(LojaDeTeste.operacao());
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    private static final String PEDIDO_MINIMO = "/api/v1/merchants/%s/settings/pedido-minimo";
    private static final String DESCONTO_RETIRADA = "/api/v1/merchants/%s/settings/desconto-retirada";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> valores(Map<String, Object> corpo, String campo) {
        return (Map<String, Object>) corpo.get(campo);
    }

    private RestTestClient.ResponseSpec put(String caminho, UUID estabelecimento, UUID quem, String corpo) {
        return cliente().put().uri(caminho.formatted(estabelecimento))
                .header("Authorization", "Bearer " + IDENTITY.tokenDe(quem))
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .exchange();
    }

    private RestTestClient.ResponseSpec get(String caminho, UUID estabelecimento, UUID quem) {
        return cliente().get().uri(caminho.formatted(estabelecimento))
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
