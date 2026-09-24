package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.api.dto.EquipeResponse;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Equipe;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.support.EquipeDeTeste;
import com.deliveryplatform.merchant.support.IdentityDeMentira;
import com.deliveryplatform.merchant.support.Infraestrutura;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A primeira requisição autenticada e autorizada do {@code merchant-service}.
 *
 * <p>Ela atravessa tudo: a cadeia de filtros, a busca do JWKS pela rede, a
 * validação de assinatura, emissor, audiência e tempo, a conversão do
 * {@code sub} em {@code UUID}, o confronto do identificador da URL com o
 * vínculo, a checagem de permissão, e a leitura da equipe. <b>Nenhuma dessas
 * peças tinha sido percorrida por uma requisição de verdade neste serviço.</b>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "delivery.outbox.habilitado=false")
class EquipeControllerIT extends Infraestrutura {

    private static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();

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

    private UUID loja;
    private UUID marli;
    private UUID bia;

    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @BeforeEach
    void umaPizzariaComDuasPessoas() {
        loja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        marli = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora())).getUsuarioId();
        bia = membros.salvar(Membro.colaborador(
                UUID.randomUUID(), loja, EquipeDeTeste.DA_BIA, agora())).getUsuarioId();
    }

    private RestTestClient cliente() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    private RestTestClient.ResponseSpec pedirEquipe(UUID lojaDaUrl, String token) {
        var requisicao = cliente().get().uri("/api/v1/merchants/" + lojaDaUrl + "/team");
        if (token != null) {
            requisicao = requisicao.header("Authorization", "Bearer " + token);
        }
        return requisicao.exchange();
    }

    // ── o caminho feliz ─────────────────────────────────────────────────────

    @Test
    void a_dona_ve_a_equipe_da_propria_loja() {
        EquipeResponse equipe = pedirEquipe(loja, IDENTITY.tokenDe(marli))
                .expectStatus().isOk()
                .expectBody(EquipeResponse.class)
                .returnResult().getResponseBody();

        assertThat(equipe).isNotNull();
        assertThat(equipe.estabelecimentoId()).isEqualTo(loja);
        assertThat(equipe.membros()).hasSize(2);
        assertThat(equipe.membros())
                .anySatisfy(membro -> {
                    assertThat(membro.usuarioId()).isEqualTo(marli);
                    assertThat(membro.papel()).isEqualTo(Papel.ADMINISTRADOR);
                    assertThat(membro.permissoes()).contains(Permissao.GERENCIAR_EQUIPE);
                });
    }

    @Test
    void a_resposta_nao_traz_nome_nem_telefone_de_ninguem() {
        String corpo = pedirEquipe(loja, IDENTITY.tokenDe(marli))
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(corpo)
                .as("quem é a pessoa por trás do usuarioId é dado do identity-service, e "
                        + "a ADR-001 proíbe este serviço de importá-lo — a tela mostra "
                        + "identificadores até a porta existir")
                .doesNotContain("Marli")
                .doesNotContain("+55");
    }

    @Test
    void quem_foi_removido_sai_da_lista_sem_sair_da_tabela() {
        Membro autora = membros.buscarPorUsuarioELoja(marli, loja).orElseThrow();
        Membro alvo = membros.buscarPorUsuarioELoja(bia, loja).orElseThrow();
        Equipe equipe = Equipe.de(loja, List.of(autora, alvo));
        equipe.remover(autora, alvo, agora());
        membros.salvar(alvo);

        EquipeResponse resposta = pedirEquipe(loja, IDENTITY.tokenDe(marli))
                .expectStatus().isOk()
                .expectBody(EquipeResponse.class)
                .returnResult().getResponseBody();

        assertThat(resposta).isNotNull();
        assertThat(resposta.membros())
                .extracting(membro -> membro.usuarioId())
                .containsExactly(marli);
        assertThat(membros.buscarPorUsuarioELoja(bia, loja))
                .as("isto é a equipe, não o histórico dela — a linha continua na tabela")
                .isPresent();
    }

    // ── 401: o token, antes de qualquer regra de negócio ────────────────────

    @Test
    void sem_token_e_401_e_nao_403() {
        pedirEquipe(loja, null).expectStatus().isUnauthorized();
    }

    @Test
    void token_de_outro_emissor_e_401() {
        // Assinado pela chave certa, com o sub certo, e de outro ambiente: o
        // padrão do Resource Server valida só tempo e deixaria este passar.
        pedirEquipe(loja, IDENTITY.tokenComEmissor("http://homologacao", marli))
                .expectStatus().isUnauthorized();
    }

    @Test
    void token_com_audiencia_de_outro_produto_e_401() {
        pedirEquipe(loja, IDENTITY.tokenComAudiencia("outro-produto", marli))
                .expectStatus().isUnauthorized();
    }

    @Test
    void token_expirado_e_401() {
        pedirEquipe(loja, IDENTITY.tokenExpirado(marli)).expectStatus().isUnauthorized();
    }

    @Test
    void token_com_assinatura_adulterada_e_401() {
        String token = IDENTITY.tokenDe(marli);
        String adulterado = token.substring(0, token.length() - 4) + "AAAA";

        pedirEquipe(loja, adulterado).expectStatus().isUnauthorized();
    }

    // ── 403: as três recusas de M7, indistinguíveis ─────────────────────────

    @Test
    void sem_vinculo_na_loja_e_403() {
        pedirEquipe(loja, IDENTITY.tokenDe(UUID.randomUUID()))
                .expectStatus().isForbidden();
    }

    @Test
    void loja_que_nao_existe_devolve_a_mesma_coisa_que_loja_que_nao_e_sua() {
        ProblemDetail semVinculo = pedirEquipe(loja, IDENTITY.tokenDe(UUID.randomUUID()))
                .expectStatus().isForbidden()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();

        ProblemDetail lojaInexistente = pedirEquipe(UUID.randomUUID(), IDENTITY.tokenDe(marli))
                .expectStatus().isForbidden()
                .expectBody(ProblemDetail.class).returnResult().getResponseBody();

        // instance é o caminho da requisição, e os dois caminhos usam UUIDs
        // diferentes por construção do teste — comparar o corpo inteiro como
        // string compararia esse acidente, não o que M7 promete.
        assertThat(lojaInexistente).isNotNull();
        assertThat(semVinculo).isNotNull();
        assertThat(lojaInexistente.getStatus()).isEqualTo(semVinculo.getStatus());
        assertThat(lojaInexistente.getTitle())
                .as("respostas diferentes transformariam a rota num scanner de "
                        + "estabelecimentos escrito em códigos de status (M7)")
                .isEqualTo(semVinculo.getTitle());
        assertThat(lojaInexistente.getDetail()).isEqualTo(semVinculo.getDetail());
    }

    @Test
    void vinculo_sem_gerenciar_equipe_e_403() {
        // A Bia tem vínculo ativo e não tem a permissão — e recebe exatamente a
        // mesma recusa de quem não tem vínculo nenhum.
        pedirEquipe(loja, IDENTITY.tokenDe(bia)).expectStatus().isForbidden();
    }

    @Test
    void token_com_sub_que_nao_e_uuid_e_403_e_nao_500() {
        pedirEquipe(loja, IDENTITY.tokenComSujeito("nao-sou-um-uuid"))
                .expectStatus().isForbidden();
    }

    // ── o actuator, que é a única exceção ───────────────────────────────────

    @Test
    void o_health_responde_sem_token() {
        // Probe de contêiner não carrega credencial — e um health protegido
        // reinicia o serviço em laço.
        cliente().get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
