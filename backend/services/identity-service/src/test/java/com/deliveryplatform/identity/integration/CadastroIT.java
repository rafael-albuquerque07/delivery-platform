package com.deliveryplatform.identity.integration;

import com.deliveryplatform.identity.api.dto.LoginRequest;
import com.deliveryplatform.identity.api.dto.LoginResponse;
import com.deliveryplatform.identity.api.dto.SignupRequest;
import com.deliveryplatform.identity.api.dto.SignupResponse;
import com.deliveryplatform.identity.api.dto.VerificationCodeRequest;
import com.deliveryplatform.identity.application.port.out.CodigoDeVerificacaoRepositorio;
import com.deliveryplatform.identity.application.port.out.UsuarioRepositorio;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.support.GeradorDeChaveDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O cadastro inteiro, pela porta da frente (ADR-042).
 *
 * <p><b>O passo 2 deste teste é literalmente o que a pessoa do onboarding
 * faz:</b> ler o código da tabela. Enquanto o transporte for humano, é esse o
 * canal — e um teste que o percorre é a prova de que o fluxo é operável, não só
 * compilável.
 *
 * <p>Sem {@code @Transactional}: o cadastro atravessa duas transações de
 * propósito (ver {@code CadastrarUsuarioService}), e uma transação de teste
 * envolvendo tudo esconderia justamente o que essa separação existe para
 * garantir. Cada caso usa um telefone próprio, porque nada é desfeito ao fim.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CadastroIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void chaveDeAssinatura(DynamicPropertyRegistry registry) {
        registry.add("delivery.jwt.private-key-path", GeradorDeChaveDeTeste::caminhoDaChaveUnica);
    }

    @LocalServerPort
    int porta;

    @Autowired
    CodigoDeVerificacaoRepositorio codigos;

    @Autowired
    UsuarioRepositorio usuarios;

    private RestTestClient cliente() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    /** O que o operador faz: abre a tabela e lê o número. */
    private String codigoEntregueAoComerciante(String telefone) {
        return codigos.buscarPorTelefone(Telefone.de(telefone)).orElseThrow().getCodigo();
    }

    private void pedirCodigo(String telefone) {
        cliente().post().uri("/api/v1/auth/verification-code")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerificationCodeRequest(telefone))
                .exchange()
                .expectStatus().isAccepted();
    }

    // ── o caminho inteiro ───────────────────────────────────────────────────

    @Test
    void do_telefone_ao_token_sem_ninguem_dar_insert_na_mao() {
        String telefone = "11911110001";

        pedirCodigo(telefone);
        String codigo = codigoEntregueAoComerciante(telefone);

        SignupResponse criado = cliente().post().uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SignupRequest(telefone, codigo, "Marli da Pizzaria", "senha-da-marli"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(SignupResponse.class)
                .returnResult().getResponseBody();

        assertThat(criado).isNotNull();
        assertThat(criado.id()).isNotNull();

        // O código morreu ao ser usado.
        assertThat(codigos.buscarPorTelefone(Telefone.de(telefone))).isEmpty();

        // E o telefone nasceu verificado porque alguém provou que o atende.
        var usuario = usuarios.buscarPorId(criado.id()).orElseThrow();
        assertThat(usuario.telefoneVerificado()).isTrue();
        assertThat(usuario.getNome()).isEqualTo("Marli da Pizzaria");

        LoginResponse token = cliente().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(telefone, "senha-da-marli"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult().getResponseBody();

        assertThat(token).isNotNull();
        assertThat(token.accessToken())
                .as("é a primeira vez no projeto que uma conta criada pela API entra "
                        + "por ela — até aqui todo usuário nascia de um INSERT")
                .isNotBlank();
    }

    @Test
    void pedir_o_codigo_de_novo_substitui_o_anterior() {
        String telefone = "11911110002";

        pedirCodigo(telefone);
        String primeiro = codigoEntregueAoComerciante(telefone);

        pedirCodigo(telefone);
        String segundo = codigoEntregueAoComerciante(telefone);

        cliente().post().uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SignupRequest(telefone, primeiro, "Marli", "senha-da-marli"))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(segundo).isNotEqualTo(primeiro);
    }

    // ── o que o cadastro não conta ──────────────────────────────────────────

    @Test
    void telefone_que_ja_tem_conta_recebe_202_e_nenhum_codigo_e_criado() {
        String telefone = "11911110003";

        pedirCodigo(telefone);
        cliente().post().uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SignupRequest(
                        telefone, codigoEntregueAoComerciante(telefone), "Marli", "senha-da-marli"))
                .exchange()
                .expectStatus().isCreated();

        pedirCodigo(telefone);

        assertThat(codigos.buscarPorTelefone(Telefone.de(telefone)))
                .as("mesma resposta, nenhum código — quem varre DDDs não descobre nada")
                .isEmpty();
    }

    @Test
    void codigo_errado_devolve_400() {
        String telefone = "11911110004";
        pedirCodigo(telefone);

        cliente().post().uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SignupRequest(telefone, "000000", "Marli", "senha-da-marli"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void cadastro_sem_ter_pedido_codigo_devolve_400() {
        cliente().post().uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SignupRequest("11911110005", "123456", "Marli", "senha-da-marli"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── a borda ─────────────────────────────────────────────────────────────

    @Test
    void telefone_malformado_devolve_400_em_vez_de_202() {
        cliente().post().uri("/api/v1/auth/verification-code")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerificationCodeRequest("abc"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void senha_curta_demais_devolve_400_antes_de_gastar_o_codigo() {
        String telefone = "11911110006";
        pedirCodigo(telefone);
        String codigo = codigoEntregueAoComerciante(telefone);

        cliente().post().uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SignupRequest(telefone, codigo, "Marli", "curta"))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(codigos.buscarPorTelefone(Telefone.de(telefone)).orElseThrow().getTentativas())
                .as("a validação de formato é anterior ao caso de uso — um erro de "
                        + "digitação na senha não pode queimar uma das cinco tentativas")
                .isZero();
    }

    @Test
    void as_duas_rotas_do_cadastro_sao_publicas() {
        // Sem esta garantia o cadastro exigiria token para criar a conta que
        // emite o primeiro token — o mesmo nó que a ADR-037 §1 desatou no login.
        cliente().post().uri("/api/v1/auth/verification-code")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerificationCodeRequest("11911110007"))
                .exchange()
                .expectStatus().isAccepted();
    }
}
