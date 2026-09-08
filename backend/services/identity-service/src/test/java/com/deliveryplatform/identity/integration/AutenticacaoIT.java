package com.deliveryplatform.identity.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.deliveryplatform.identity.application.port.out.CodificadorDeSenha;
import com.deliveryplatform.identity.application.port.out.UsuarioRepositorio;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.domain.model.Usuario;
import com.deliveryplatform.identity.support.GeradorDeChaveDeTeste;

/**
 * O primeiro token do projeto, ida e volta.
 *
 * <p>A asserção que importa mais não é o 200: é que o token emitido com a chave
 * privada é aceito por um decoder construído <b>a partir do JWKS publicado</b>.
 * É esse par que os outros oito serviços vão exercer, e é a única forma de
 * provar que a metade pública corresponde à privada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
class AutenticacaoIT {

    private static final String TELEFONE = "11987654321";
    private static final String SENHA = "a-senha-da-marli";

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
    UsuarioRepositorio usuarios;

    @Autowired
    CodificadorDeSenha codificador;

    RestTestClient http;
    Usuario marli;

    @BeforeAll
    void semear() {
        marli = Usuario.novo("Marli", Telefone.de(TELEFONE), Instant.now(), codificador.codificar(SENHA));
        usuarios.salvar(marli);
    }

    @BeforeEach
    void construirCliente() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    private String postarLogin(String corpo, HttpStatus esperado) {
        var resultado = http.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .exchange()
                .expectBody(String.class)
                .returnResult();

        assertThat(resultado.getStatus()).isEqualTo(esperado);
        return resultado.getResponseBody();
    }

    private String tokenDeLoginBemSucedido() {
        String corpo = postarLogin(
                "{\"telefone\":\"" + TELEFONE + "\",\"senha\":\"" + SENHA + "\"}", HttpStatus.OK);
        return corpo.replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private NimbusJwtDecoder decoderDoJwksPublicado() {
        return NimbusJwtDecoder
                .withJwkSetUri("http://localhost:" + porta + "/.well-known/jwks.json")
                .build();
    }

    @Test
    void credenciais_certas_devolvem_token_do_tipo_bearer() {
        String corpo = postarLogin(
                "{\"telefone\":\"" + TELEFONE + "\",\"senha\":\"" + SENHA + "\"}", HttpStatus.OK);

        assertThat(corpo).contains("\"tokenType\":\"Bearer\"", "\"expiresIn\":1800");
        assertThat(corpo)
                .as("o token já carrega o sub; nome e e-mail no corpo do login espalhariam dado pessoal")
                .doesNotContain("Marli", "hashDaSenha", "bcrypt");
    }

    @Test
    void o_token_emitido_e_aceito_pela_chave_publicada_no_jwks() {
        Jwt jwt = decoderDoJwksPublicado().decode(tokenDeLoginBemSucedido());

        assertThat(jwt.getSubject())
                .as("o sub é o id do usuário, nunca o telefone — telefone muda de dono (U9)")
                .isEqualTo(marli.getId().toString());
    }

    @Test
    void o_token_tem_exatamente_os_seis_claims_da_adr_015() {
        Jwt jwt = decoderDoJwksPublicado().decode(tokenDeLoginBemSucedido());

        assertThat(jwt.getClaims().keySet())
                .as("roles e scope saíram na emenda de 26/08 e não podem voltar sem alguém decidir")
                .containsExactlyInAnyOrder("iss", "sub", "aud", "iat", "exp", "jti");
    }

    @Test
    void o_cabecalho_traz_o_kid_da_chave_publicada() {
        Jwt jwt = decoderDoJwksPublicado().decode(tokenDeLoginBemSucedido());

        assertThat(jwt.getHeaders().get("kid"))
                .as("sem kid no cabeçalho, um JWKS com duas chaves não se resolve — e duas chaves "
                        + "é exatamente o estado de uma rotação")
                .isNotNull();
    }

    @Test
    void o_aud_e_o_publico_unico_e_o_iss_e_o_emissor_configurado() {
        Jwt jwt = decoderDoJwksPublicado().decode(tokenDeLoginBemSucedido());

        assertThat(jwt.getAudience()).containsExactly("delivery-platform");
        assertThat(jwt.getIssuer().toString()).isEqualTo("http://localhost:8081");
    }

    @Test
    void senha_errada_e_telefone_inexistente_dao_a_mesma_resposta() {
        String comSenhaErrada = postarLogin(
                "{\"telefone\":\"" + TELEFONE + "\",\"senha\":\"errada\"}", HttpStatus.UNAUTHORIZED);

        String comTelefoneInexistente = postarLogin(
                "{\"telefone\":\"11900000000\",\"senha\":\"" + SENHA + "\"}", HttpStatus.UNAUTHORIZED);

        assertThat(comSenhaErrada)
                .as("resposta diferente por caso transforma o login em verificador de cadastro, "
                        + "e o identificador aqui é um telefone — varredura barata")
                .isEqualTo(comTelefoneInexistente);
    }

    @Test
    void telefone_impossivel_de_normalizar_tambem_e_401() {
        postarLogin("{\"telefone\":\"isto não é telefone\",\"senha\":\"x\"}", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void json_malformado_e_400_e_nao_401() {
        postarLogin("{\"telefone\":", HttpStatus.BAD_REQUEST);
    }

    @Test
    void campo_ausente_e_400_e_nao_401() {
        postarLogin("{\"telefone\":\"" + TELEFONE + "\"}", HttpStatus.BAD_REQUEST);
    }
}
