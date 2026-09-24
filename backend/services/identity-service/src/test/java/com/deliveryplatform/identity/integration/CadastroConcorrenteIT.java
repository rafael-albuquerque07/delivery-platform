package com.deliveryplatform.identity.integration;

import com.deliveryplatform.identity.support.GeradorDeChaveDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A dívida que a rodada do cadastro deixou aberta, paga.
 *
 * <p>O {@code CadastrarUsuarioService} tem um
 * {@code catch (DataIntegrityViolationException)} para o caso de duas inscrições
 * com o mesmo telefone chegarem juntas — as duas passam pela verificação de
 * existência, e é o índice único do banco que decide. O único teste que esse
 * {@code catch} tinha usava um dublê <b>que já lançava o tipo traduzido</b>, ou
 * seja: provava que o {@code catch} pega o que o próprio teste jogou nele.
 *
 * <p>A tradução de {@code ConstraintViolationException} do Hibernate para
 * {@code DataIntegrityViolationException} do Spring só acontece em chamada que
 * atravessa um bean proxiado por {@code @Repository} — foi exatamente esse
 * detalhe que derrubou dois testes da B1. Então o dublê não estava provando o
 * caminho: estava pulando o pedaço onde ele pode quebrar.
 *
 * <p>Aqui são <b>duas requisições HTTP de verdade, ao mesmo tempo, contra um
 * PostgreSQL de verdade</b>. É o mesmo formato do {@code SaidaConcorrenteIT} da
 * B1, pelo mesmo motivo: corrida não se testa com dublê, porque o dublê não tem
 * o índice único que decide quem ganha.
 *
 * <p><b>O que se afirma não é o código de status exato</b> — é que exatamente uma
 * inscrição nasce, e que a que perdeu recebe recusa de cliente. Um {@code 500}
 * aqui significaria que a corrida escapou do {@code catch} e chegou ao usuário
 * como defeito do servidor.
 *
 * <p><b>Cada caso usa um telefone próprio</b>, como o {@code CadastroIT}: o
 * contêiner é da classe e nada é desfeito entre os casos, e um telefone que já
 * tem conta não recebe código novo (ADR-042 §5).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CadastroConcorrenteIT {

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
    JdbcTemplate jdbc;

    private RestTestClient cliente() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }

    private String pedirCodigo(String telefone) {
        cliente().post().uri("/api/v1/auth/verification-code")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"telefone": "%s"}
                        """.formatted(telefone))
                .exchange()
                .expectStatus().isAccepted();

        // O código está em texto claro na tabela, e é de propósito: enquanto o
        // transporte for humano, a tabela É o canal de entrega (ADR-042). O
        // teste lê pelo mesmo caminho que a pessoa que hoje entrega o código
        // leria — no dia em que existir CanalPort e o código virar hash, este
        // teste muda junto, e é bom que mude.
        return jdbc.queryForObject(
                "select codigo from codigo_de_verificacao where telefone = ? order by criado_em desc limit 1",
                String.class, telefone);
    }

    private int inscrever(String telefone, String codigo) {
        return cliente().post().uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"telefone": "%s", "codigo": "%s", "nome": "Marli", "senha": "uma-senha-bem-comprida-123"}
                        """.formatted(telefone, codigo))
                .exchange()
                .expectBody()
                .returnResult()
                .getStatus()
                .value();
    }

    @Test
    void duas_inscricoes_no_mesmo_instante_e_so_uma_conta_nasce() throws Exception {
        String telefone = "+5511988887777";
        String codigo = pedirCodigo(telefone);

        var largada = new CountDownLatch(1);
        var chegada = new CountDownLatch(2);
        var status = new AtomicInteger[]{new AtomicInteger(), new AtomicInteger()};

        for (int i = 0; i < 2; i++) {
            int qual = i;
            Thread.ofPlatform().start(() -> {
                try {
                    largada.await();
                    status[qual].set(inscrever(telefone, codigo));
                } catch (Exception e) {
                    status[qual].set(-1);
                } finally {
                    chegada.countDown();
                }
            });
        }

        largada.countDown();
        assertThat(chegada.await(30, TimeUnit.SECONDS))
                .as("as duas requisições precisam terminar — travar aqui seria deadlock")
                .isTrue();

        List<Integer> respostas = List.of(status[0].get(), status[1].get());

        assertThat(respostas)
                .as("uma ganha o índice único")
                .contains(201);

        assertThat(respostas)
                .as("e a que perde recebe recusa de cliente: um 500 aqui significa que a "
                        + "corrida escapou do catch e chegou ao usuário como defeito do servidor")
                .allSatisfy(codigoHttp -> assertThat(codigoHttp).isBetween(200, 499));

        Integer contas = jdbc.queryForObject(
                "select count(*) from usuario where telefone = ?", Integer.class, telefone);
        assertThat(contas)
                .as("o índice único é quem decide, e ele decide por exatamente uma")
                .isEqualTo(1);
    }

    /**
     * O caminho sequencial: um código <b>válido</b> para um telefone que já tem
     * conta.
     *
     * <p>Pela API esse estado não se fabrica — pedir código para telefone com
     * conta não gera código (ADR-042 §5), e o da primeira inscrição foi apagado
     * ao ser usado. Mas ele existe de verdade: é o código pedido antes de outra
     * requisição criar a conta, que é exatamente a janela que o
     * {@code existeComTelefone} depois do {@code confere} guarda. A linha é
     * escrita por baixo para montar essa janela sem depender de relógio.
     */
    @Test
    void a_segunda_inscricao_depois_da_primeira_e_recusada_sem_erro_de_servidor() {
        String telefone = "+5511988886666";
        assertThat(inscrever(telefone, pedirCodigo(telefone))).isEqualTo(201);

        jdbc.update("""
                insert into codigo_de_verificacao (id, telefone, codigo, criado_em, expira_em)
                values (?, ?, '123456', now(), now() + interval '10 minutes')
                """, UUID.randomUUID(), telefone);

        assertThat(inscrever(telefone, "123456"))
                .as("este é o caminho sequencial, que a verificação de existência pega "
                        + "antes do banco — e que também não pode virar 500")
                .isBetween(400, 499);
    }
}
