package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.in.LojaDoUsuario;
import com.deliveryplatform.merchant.application.port.out.ConviteRepositorio;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.support.EquipeDeTeste;
import com.deliveryplatform.merchant.support.IdentityDeMentira;
import com.deliveryplatform.merchant.support.Infraestrutura;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@code LANCAR_PEDIDO} (M3-3; ADR-011, emenda; ADR-086, fatia 3): o fundador a recebe, o
 * convite a dá, e a migration {@code V9} a leva — com a {@code CONFIGURAR_LOJA} que faltava — ao
 * fundador que já existia.
 *
 * <p><b>Os casos 4 e 5 não usam o esquema que o Spring migrou.</b> Eles migram um esquema à
 * parte, {@code prova_v9}, pelo Flyway pela API, até a {@code V8}; gravam os membros como estavam
 * antes da {@code V9}; e migram o resto. A preparação é <b>uma só</b>, em {@code @BeforeAll}, e os
 * dois casos só leem: com uma preparação por caso, o segundo gravaria depois de a {@code V9} já
 * ter rodado, e ficaria vermelho pela ordem do JUnit, não por defeito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "delivery.outbox.habilitado=false")
class PermissaoDeLancarIT extends Infraestrutura {

    private static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();

    private static final String ESQUEMA = "prova_v9";

    private static final List<String> AS_DEZ = List.of(
            "VER_PRODUTO", "CRIAR_PRODUTO", "ALTERAR_PRODUTO", "DESATIVAR_PRODUTO",
            "VER_PEDIDO", "ALTERAR_STATUS", "VER_VENDAS", "VER_ENTREGA",
            "GERENCIAR_EQUIPE", "GERENCIAR_JORNADA");

    private static final Instant T0 = Instant.parse("2026-10-01T12:00:00Z");

    // os membros do esquema à parte, gravados antes da V9
    private static final UUID FUNDADOR_ANTIGO = UUID.randomUUID();
    private static final UUID COLABORADOR_DE_TRES = UUID.randomUUID();
    private static final UUID PROMOVIDO_DE_CINCO = UUID.randomUUID();
    private static final UUID FUNDADOR_DA_E4 = UUID.randomUUID();
    private static final UUID COLABORADOR_COM_AS_DEZ = UUID.randomUUID();
    private static final UUID PROMOVIDO_COM_AS_DEZ = UUID.randomUUID();
    private static final UUID FUNDADOR_QUE_PERDEU = UUID.randomUUID();
    private static final UUID FUNDADOR_DO_EMPATE = UUID.randomUUID();
    private static final UUID COLABORADOR_DO_EMPATE = UUID.randomUUID();

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
    @Autowired ConviteRepositorio convites;

    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    // ── a permissão no código ───────────────────────────────────────────────

    @Test
    @DisplayName("1 · cadastra uma loja, e o contexto de acesso do fundador traz LANCAR_PEDIDO")
    void o_fundador_a_recebe() {
        UUID marli = UUID.randomUUID();
        LojaDoUsuario criada = cliente().post().uri("/api/v1/me/estabelecimentos")
                .header("Authorization", "Bearer " + IDENTITY.tokenDe(marli))
                .contentType(MediaType.APPLICATION_JSON)
                .body(CriarEstabelecimentoIT.CORPO.formatted("Cantina do Zé", "12.345.678/0001-95"))
                .exchange().expectStatus().isCreated()
                .expectBody(LojaDoUsuario.class).returnResult().getResponseBody();

        assertThat(permissoesNoContexto(criada.estabelecimentoId(), marli)).contains("LANCAR_PEDIDO");
    }

    @Test
    @DisplayName("2 · o fundador convida com LANCAR_PEDIDO, o convidado aceita, e o contexto dele traz só as do convite")
    void o_convite_a_da() {
        UUID loja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        UUID marli = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora())).getUsuarioId();

        String token = (String) convidar(loja, marli, "\"VER_PEDIDO\",\"LANCAR_PEDIDO\"")
                .expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody().get("token");
        UUID atendente = UUID.randomUUID();
        cliente().post().uri("/api/v1/convites/" + token + "/aceite")
                .header("Authorization", "Bearer " + IDENTITY.tokenDe(atendente))
                .exchange().expectStatus().isOk();

        assertThat(permissoesNoContexto(loja, atendente))
                .containsExactlyInAnyOrder("VER_PEDIDO", "LANCAR_PEDIDO");
    }

    /** A recusa é a da emissão, copiada do {@code ConvidarIT} ("A2 na emissão"). */
    @Test
    @DisplayName("3 · quem não tem LANCAR_PEDIDO não a oferece: 400 com a frase, e nada é gravado")
    void quem_nao_tem_nao_oferece() {
        UUID loja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora()));
        UUID junior = membros.salvar(Membro.colaborador(
                UUID.randomUUID(), loja, EquipeDeTeste.DO_JUNIOR, agora())).getUsuarioId();
        assertThat(EquipeDeTeste.DO_JUNIOR).doesNotContain(Permissao.LANCAR_PEDIDO);

        ProblemDetail recusa = convidar(loja, junior, "\"LANCAR_PEDIDO\"")
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();

        assertThat(recusa.getDetail()).isEqualTo("você só pode oferecer permissões que você mesmo tem");
        assertThat(convites.pendentesDe(loja)).isEmpty();
    }

    // ── a V9, num esquema à parte ───────────────────────────────────────────

    /**
     * Quatro lojas, com os membros gravados como estavam antes da {@code V9}. Na primeira, o
     * fundador antigo é o primeiro membro, e os outros vêm depois. O fundador da E-4 em diante tem
     * loja própria, porque também é o primeiro da dele. Na terceira, o primeiro membro é um
     * administrador com só três permissões — o fundador que perdeu permissões. Na quarta, um
     * colaborador com as dez nasceu no mesmo instante do fundador, como a semeadura pode fazer.
     * Cada condição da {@code V9} só se prova com um membro que passa nas outras duas e falha nela.
     */
    @BeforeAll
    static void migraAteAV8GravaOsMembrosEMigraOResto() throws SQLException {
        flyway("8").migrate();

        UUID loja = UUID.randomUUID();
        UUID lojaDaE4 = UUID.randomUUID();
        UUID lojaDoQuePerdeu = UUID.randomUUID();
        UUID lojaDoEmpate = UUID.randomUUID();
        try (Connection c = conexao()) {
            gravarLoja(c, loja);
            gravarLoja(c, lojaDaE4);
            gravarLoja(c, lojaDoQuePerdeu);
            gravarLoja(c, lojaDoEmpate);

            gravarMembro(c, FUNDADOR_ANTIGO, loja, "ADMINISTRADOR", T0, AS_DEZ);
            gravarMembro(c, COLABORADOR_DE_TRES, loja, "COLABORADOR", T0.plusSeconds(60),
                    List.of("VER_PRODUTO", "VER_PEDIDO", "ALTERAR_STATUS"));
            gravarMembro(c, PROMOVIDO_DE_CINCO, loja, "ADMINISTRADOR", T0.plusSeconds(120),
                    List.of("VER_PRODUTO", "CRIAR_PRODUTO", "VER_PEDIDO", "ALTERAR_STATUS", "VER_VENDAS"));
            gravarMembro(c, COLABORADOR_COM_AS_DEZ, loja, "COLABORADOR", T0.plusSeconds(180), AS_DEZ);
            gravarMembro(c, PROMOVIDO_COM_AS_DEZ, loja, "ADMINISTRADOR", T0.plusSeconds(240), AS_DEZ);

            List<String> daE4 = new ArrayList<>(AS_DEZ);
            daE4.add("CONFIGURAR_LOJA");
            gravarMembro(c, FUNDADOR_DA_E4, lojaDaE4, "ADMINISTRADOR", T0, daE4);

            gravarMembro(c, FUNDADOR_QUE_PERDEU, lojaDoQuePerdeu, "ADMINISTRADOR", T0,
                    List.of("VER_PRODUTO", "VER_PEDIDO", "GERENCIAR_EQUIPE"));

            gravarMembro(c, FUNDADOR_DO_EMPATE, lojaDoEmpate, "ADMINISTRADOR", T0, AS_DEZ);
            gravarMembro(c, COLABORADOR_DO_EMPATE, lojaDoEmpate, "COLABORADOR", T0, AS_DEZ);
        }

        flyway(null).migrate();
    }

    @Test
    @DisplayName("4 · a V9: o fundador antigo, com as dez e sem CONFIGURAR_LOJA, passa a ter LANCAR_PEDIDO e CONFIGURAR_LOJA")
    void a_v9_alcanca_o_fundador_antigo() throws SQLException {
        assertThat(permissoesNoEsquema(FUNDADOR_ANTIGO))
                .containsExactlyInAnyOrderElementsOf(comMais(AS_DEZ, "LANCAR_PEDIDO", "CONFIGURAR_LOJA"));
    }

    @Test
    @DisplayName("5 · a V9: colaborador de três, promovido de cinco, colaborador com as dez, promovido com as dez, fundador que perdeu permissões e colaborador do empate não ganham nada; o fundador da E-4 ganha só LANCAR_PEDIDO, sem duplicar; o fundador do empate ganha as duas")
    void a_v9_nao_alcanca_mais_ninguem() throws SQLException {
        assertThat(permissoesNoEsquema(COLABORADOR_DE_TRES))
                .containsExactlyInAnyOrder("VER_PRODUTO", "VER_PEDIDO", "ALTERAR_STATUS");
        assertThat(permissoesNoEsquema(PROMOVIDO_DE_CINCO))
                .containsExactlyInAnyOrder("VER_PRODUTO", "CRIAR_PRODUTO", "VER_PEDIDO", "ALTERAR_STATUS", "VER_VENDAS");
        assertThat(permissoesNoEsquema(COLABORADOR_COM_AS_DEZ)).containsExactlyInAnyOrderElementsOf(AS_DEZ);
        assertThat(permissoesNoEsquema(PROMOVIDO_COM_AS_DEZ)).containsExactlyInAnyOrderElementsOf(AS_DEZ);
        assertThat(permissoesNoEsquema(FUNDADOR_DA_E4))
                .containsExactlyInAnyOrderElementsOf(comMais(AS_DEZ, "CONFIGURAR_LOJA", "LANCAR_PEDIDO"));
        assertThat(permissoesNoEsquema(FUNDADOR_QUE_PERDEU))
                .containsExactlyInAnyOrder("VER_PRODUTO", "VER_PEDIDO", "GERENCIAR_EQUIPE");
        assertThat(permissoesNoEsquema(FUNDADOR_DO_EMPATE))
                .containsExactlyInAnyOrderElementsOf(comMais(AS_DEZ, "LANCAR_PEDIDO", "CONFIGURAR_LOJA"));
        assertThat(permissoesNoEsquema(COLABORADOR_DO_EMPATE)).containsExactlyInAnyOrderElementsOf(AS_DEZ);
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    private RestTestClient cliente() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    private RestTestClient.ResponseSpec convidar(UUID loja, UUID quem, String permissoes) {
        return cliente().post().uri("/api/v1/merchants/" + loja + "/team/convites")
                .header("Authorization", "Bearer " + IDENTITY.tokenDe(quem))
                .contentType(MediaType.APPLICATION_JSON)
                .body(ConvidarIT.CORPO.formatted("(81) 98765-4321", permissoes))
                .exchange();
    }

    @SuppressWarnings("unchecked")
    private List<String> permissoesNoContexto(UUID loja, UUID quem) {
        Map<String, Object> contexto = cliente().get()
                .uri("/internal/merchants/" + loja + "/me/contexto-de-acesso")
                .header("Authorization", "Bearer " + IDENTITY.tokenDe(quem))
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        return (List<String>) contexto.get("permissoes");
    }

    private static Flyway flyway(String alvo) {
        var configuracao = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(ESQUEMA)
                .defaultSchema(ESQUEMA)
                .locations("classpath:db/migration");
        if (alvo != null) {
            configuracao = configuracao.target(alvo);
        }
        return configuracao.load();
    }

    private static Connection conexao() throws SQLException {
        Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        c.setSchema(ESQUEMA);
        return c;
    }

    private static void gravarLoja(Connection c, UUID id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                insert into estabelecimento (id, nome, documento, telefone, endereco_textual, bairro,
                    fuso_horario, tipo_de_operacao, desconto_de_retirada, fundo_maximo_de_troco,
                    aceita_pedido_sem_troco_disponivel, pausa_ativa)
                values (?, 'Pizzaria', '12345678000195', '81987654321', 'Rua da Aurora, 10', 'Boa Vista',
                    'America/Recife', 'PRODUCAO', 0, 50, false, false)""")) {
            s.setObject(1, id);
            s.executeUpdate();
        }
    }

    private static void gravarMembro(Connection c, UUID id, UUID loja, String papel, Instant criadoEm,
                                     List<String> permissoes) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                insert into membro (id, usuario_id, estabelecimento_id, papel, estado, criado_em, alterado_em)
                values (?, ?, ?, ?, 'ATIVO', ?, ?)""")) {
            s.setObject(1, id);
            s.setObject(2, UUID.randomUUID());
            s.setObject(3, loja);
            s.setString(4, papel);
            s.setTimestamp(5, Timestamp.from(criadoEm));
            s.setTimestamp(6, Timestamp.from(criadoEm));
            s.executeUpdate();
        }
        try (PreparedStatement s = c.prepareStatement(
                "insert into membro_permissao (membro_id, permissao) values (?, ?)")) {
            for (String p : permissoes) {
                s.setObject(1, id);
                s.setString(2, p);
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    private static List<String> permissoesNoEsquema(UUID membro) throws SQLException {
        List<String> lidas = new ArrayList<>();
        try (Connection c = conexao();
             PreparedStatement s = c.prepareStatement(
                     "select permissao from membro_permissao where membro_id = ?")) {
            s.setObject(1, membro);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    lidas.add(r.getString(1));
                }
            }
        }
        return lidas;
    }

    private static List<String> comMais(List<String> base, String... mais) {
        List<String> todas = new ArrayList<>(base);
        todas.addAll(List.of(mais));
        return todas;
    }
}
