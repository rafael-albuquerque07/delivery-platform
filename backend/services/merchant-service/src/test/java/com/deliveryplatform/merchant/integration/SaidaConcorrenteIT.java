package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.exception.LojaFicariaSemAdministrador;
import com.deliveryplatform.merchant.domain.model.Equipe;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A prova de que M6 sobrevive a duas pessoas saindo ao mesmo tempo.
 *
 * <p><b>Por que esta classe existe separada.</b> As outras ITs são
 * {@code @Transactional} e voltam atrás no fim; aqui é o oposto — o teste
 * <b>precisa</b> de transações de verdade, que confirmam, porque é o confirmar
 * de uma que faz a outra enxergar o mundo mudado. Uma transação de teste
 * envolvendo as duas esconderia exatamente o que se quer medir.
 *
 * <p>É a regra do repositório aplicada ao cadeado: <i>peça que nunca rodou não
 * é peça, é intenção</i>. Um {@code for update} escrito e nunca contendido é
 * uma linha de SQL com aparência de garantia.
 *
 * <p><b>Cada caso limpa o que criou</b>, porque nada aqui é desfeito sozinho.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.rabbitmq.username=teste",
        "spring.rabbitmq.password=teste"
})
@Testcontainers
class SaidaConcorrenteIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MembroRepositorio membros;

    @Autowired
    private EstabelecimentoRepositorio lojas;

    /**
     * Construído à mão a partir do gerenciador de transações, e não injetado.
     * O Spring Boot registra um {@code TransactionTemplate} por
     * autoconfiguração, mas confiar nisso é o tipo de suposição que este
     * repositório já pagou cinco vezes — e construí-lo custa uma linha.
     */
    private TransactionTemplate tx;

    private UUID loja;
    private UUID marli;
    private UUID socio;

    @Autowired
    void montarOTemplate(PlatformTransactionManager gerenciador) {
        this.tx = new TransactionTemplate(gerenciador);
    }

    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Uma loja com <b>dois</b> administradores ativos, confirmada no banco. */
    @BeforeEach
    void duasPessoasQuerendoSair() {
        tx.executeWithoutResult(status -> {
            loja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
            marli = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora())).getUsuarioId();
            socio = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora())).getUsuarioId();
        });
    }

    /** O que a segunda transação tenta fazer: pegar a equipe e sair dela. */
    private void sair(UUID usuario) {
        tx.executeWithoutResult(status -> {
            Equipe equipe = membros.equipeParaAlteracao(loja);
            Membro quemSai = equipe.doUsuario(usuario).orElseThrow();
            equipe.sair(quemSai, agora());
            membros.salvar(quemSai);
        });
    }

    private long administradoresAtivosNoBanco() {
        return tx.execute(status -> membros.equipeParaAlteracao(loja).administradoresAtivos());
    }

    // ── em série: a checagem de A3 faz o seu trabalho ───────────────────────

    @Test
    void o_segundo_a_sair_e_recusado_porque_ficou_sozinho() {
        sair(marli);

        assertThatThrownBy(() -> sair(socio))
                .as("a primeira saída deixou um administrador ativo; a segunda o levaria "
                        + "a zero, e é a única operação do sistema capaz disso")
                .isInstanceOf(LojaFicariaSemAdministrador.class);

        assertThat(administradoresAtivosNoBanco()).isEqualTo(1);
    }

    // ── em paralelo: o cadeado faz o seu ────────────────────────────────────

    /**
     * Os dois administradores mandam "sair" ao mesmo tempo.
     *
     * <p><b>Sem o cadeado, os dois passam.</b> Cada transação leria a equipe
     * antes de a outra gravar, contaria dois administradores ativos, aprovaria a
     * saída — e a loja ficaria órfã com a checagem de A3 escrita, correta e
     * executada nas duas vezes. É o modo de falha em que o código está certo e o
     * resultado está errado.
     *
     * <p>Com o {@code for update} na linha da loja, a segunda transação só lê
     * depois de a primeira confirmar: conta um administrador ativo — ela
     * própria — e é recusada.
     *
     * <p>A asserção é sobre o resultado, não sobre quem ganhou: <b>exatamente
     * uma sai, e a loja continua com administrador</b>. Qual das duas vence é
     * assunto do escalonador.
     */
    @Test
    void duas_saidas_simultaneas_e_exatamente_uma_passa() throws Exception {
        AtomicInteger passaram = new AtomicInteger();
        AtomicInteger recusadas = new AtomicInteger();
        CountDownLatch juntos = new CountDownLatch(2);
        ExecutorService duas = Executors.newFixedThreadPool(2);

        for (UUID usuario : List.of(marli, socio)) {
            duas.submit(() -> {
                try {
                    juntos.countDown();
                    juntos.await(10, TimeUnit.SECONDS);
                    sair(usuario);
                    passaram.incrementAndGet();
                } catch (LojaFicariaSemAdministrador esperado) {
                    recusadas.incrementAndGet();
                } catch (Exception inesperado) {
                    throw new IllegalStateException(inesperado);
                }
            });
        }

        duas.shutdown();
        assertThat(duas.awaitTermination(60, TimeUnit.SECONDS))
                .as("se travou aqui, o cadeado virou espera infinita em vez de fila — "
                        + "e isso é defeito, não lentidão")
                .isTrue();

        assertThat(passaram.get() + recusadas.get())
                .as("nenhuma das duas pode ter falhado por outro motivo")
                .isEqualTo(2);
        assertThat(passaram.get()).isEqualTo(1);
        assertThat(administradoresAtivosNoBanco())
                .as("M6, que é a invariante que tudo isto existe para proteger")
                .isEqualTo(1);
    }
}
