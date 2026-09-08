package com.deliveryplatform.identity.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.deliveryplatform.identity.application.port.out.EmissorDeToken;
import com.deliveryplatform.identity.support.GeradorDeChaveDeTeste;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Congela o contrato HTTP deste serviço (ADR-039).
 *
 * <p>O documento é gerado do código pelo springdoc, comparado com o arquivo
 * commitado em {@code contracts/openapi/}, e uma divergência é falha de build.
 * A promessa antiga — <i>"um arquivo por serviço, validado no CI"</i> — durou
 * trinta e sete ADRs sem produzir um único arquivo. A diferença entre aquela
 * frase e esta classe é que esta executa.
 *
 * <p><b>Este é também o primeiro pedido autenticado da história do projeto.</b>
 * O {@code RotasPublicasIT} prova que rota protegida recusa quem não tem token;
 * o {@code AutenticacaoIT} prova que o token emitido é decodificável pela chave
 * publicada. Nenhum dos dois provava que a cadeia de filtros <b>aceita</b> um
 * token válido — o caminho feliz nunca foi percorrido. Aqui ele é.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ContratoOpenApiIT {

    private static final String SERVICO = "identity-service";

    /** Ajuste aqui se o {@code springdoc.api-docs.path} do yml não for o padrão. */
    private static final String CAMINHO_DO_DOCUMENTO = "/v3/api-docs";

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
    EmissorDeToken emissor;

    @Test
    void o_contrato_commitado_e_o_que_o_codigo_expoe() throws Exception {
        String gerado = normalizar(buscarDocumento());
        Path arquivo = arquivoDoContrato();

        if (Boolean.getBoolean("openapi.atualizar")) {
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, gerado);
            return;
        }

        assertThat(Files.exists(arquivo))
                .as("%s não existe. Gere com: ./gradlew :services:%s:test -Dopenapi.atualizar=true",
                        arquivo, SERVICO)
                .isTrue();

        assertThat(gerado)
                .as("o contrato commitado divergiu do que o código expõe. Se a mudança de "
                        + "superfície foi intencional, regrave com -Dopenapi.atualizar=true e "
                        + "revise o diff — é ele que torna a mudança visível")
                .isEqualTo(Files.readString(arquivo));
    }

    /**
     * Busca o documento com um token de verdade.
     *
     * <p>A ADR-039 §4 mantém {@code /v3/api-docs} fora das rotas públicas: o
     * contrato é publicado pelo repositório, não por um endpoint aberto que
     * descreve a superfície inteira para qualquer um. Então o teste se autentica
     * como qualquer cliente faria.
     */
    private String buscarDocumento() {
        String token = emissor.emitir(UUID.randomUUID()).valor();

        var resposta = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + porta)
                .build()
                .get().uri(CAMINHO_DO_DOCUMENTO)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody(String.class)
                .returnResult();

        assertThat(resposta.getStatus())
                .as("primeiro pedido autenticado do projeto: 401 aqui significa que a cadeia "
                        + "recusa um token que ela própria emitiu, e nenhum teste anterior "
                        + "percorria esse caminho")
                .isEqualTo(HttpStatus.OK);

        return resposta.getResponseBody();
    }

    /**
     * Forma canônica: chaves ordenadas e sem {@code servers}.
     *
     * <p>A ordenação existe porque o arquivo é comparado byte a byte, e mapa em
     * JSON não promete ordem. O {@code servers} sai porque o springdoc o preenche
     * com a URL de onde o documento foi servido — que num teste é uma porta
     * aleatória, e em produção é configuração de implantação. Endereço de
     * servidor não é contrato.
     */
    private String normalizar(String json) throws Exception {
        ObjectMapper mapeador = JsonMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();

        ObjectNode raiz = (ObjectNode) mapeador.readTree(json);
        raiz.remove("servers");

        return mapeador.writerWithDefaultPrettyPrinter().writeValueAsString(raiz) + "\n";
    }

    /**
     * Sobe a partir do diretório de trabalho até achar {@code contracts/}.
     *
     * <p>Um caminho relativo fixo — {@code ../../../contracts} — funcionaria hoje
     * e quebraria em silêncio no dia em que o Gradle mudasse o diretório de
     * trabalho do teste. Procurar por uma âncora falha alto quando não acha.
     */
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

        return atual.resolve("contracts").resolve("openapi").resolve(SERVICO + ".json");
    }
}
