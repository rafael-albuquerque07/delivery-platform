package com.deliveryplatform.identity.integration;

import com.deliveryplatform.identity.application.port.out.CodigoDeVerificacaoRepositorio;
import com.deliveryplatform.identity.domain.model.CodigoDeVerificacao;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.support.GeradorDeChaveDeTeste;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
class CodigoDeVerificacaoRepositorioJpaIT {

    private static final Telefone TELEFONE = Telefone.de("11987654321");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void chaveDeAssinatura(DynamicPropertyRegistry registry) {
        registry.add("delivery.jwt.private-key-path", GeradorDeChaveDeTeste::caminhoDaChaveUnica);
    }

    @Autowired
    private CodigoDeVerificacaoRepositorio repositorio;

    @PersistenceContext
    private EntityManager entityManager;

    /** Truncado em microssegundos: é a resolução do {@code TIMESTAMPTZ}. */
    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    void grava_e_recupera_preservando_o_codigo_com_zero_a_esquerda() {
        repositorio.substituir(CodigoDeVerificacao.novo(TELEFONE, "004291", agora()));
        entityManager.flush();
        entityManager.clear();

        Optional<CodigoDeVerificacao> recuperado = repositorio.buscarPorTelefone(TELEFONE);

        assertThat(recuperado).isPresent();
        assertThat(recuperado.orElseThrow().getCodigo())
                .as("VARCHAR e não INTEGER — como número, 004291 volta 4291")
                .isEqualTo("004291");
        assertThat(recuperado.orElseThrow().getTelefone()).isEqualTo(TELEFONE);
        assertThat(recuperado.orElseThrow().getTentativas()).isZero();
    }

    /**
     * O teste que justifica o {@code flush} do adaptador.
     *
     * <p>Sem ele o Hibernate emite o {@code INSERT} antes do {@code DELETE} —
     * ele ordena as operações da sessão por tipo — e este segundo pedido morre
     * no {@code UNIQUE (telefone)}. É o defeito que só aparece no <b>segundo</b>
     * pedido de código, que é exatamente o que ninguém faz testando à mão.
     */
    @Test
    void pedir_de_novo_substitui_o_anterior_em_vez_de_esbarrar_no_indice_unico() {
        Instant agora = agora();
        repositorio.substituir(CodigoDeVerificacao.novo(TELEFONE, "111111", agora));
        repositorio.substituir(CodigoDeVerificacao.novo(TELEFONE, "222222", agora.plusSeconds(30)));
        entityManager.flush();
        entityManager.clear();

        assertThat(repositorio.buscarPorTelefone(TELEFONE).orElseThrow().getCodigo())
                .isEqualTo("222222");
        assertThat(entityManager
                .createQuery("select count(c) from CodigoDeVerificacaoJpaEntity c", Long.class)
                .getSingleResult())
                .as("um código por telefone — a tabela cresce com telefones, não com pedidos")
                .isEqualTo(1L);
    }

    @Test
    void a_tentativa_gasta_sobrevive_a_regravacao() {
        CodigoDeVerificacao codigo = CodigoDeVerificacao.novo(TELEFONE, "111111", agora());
        codigo.confere("999999", agora());

        repositorio.substituir(codigo);
        entityManager.flush();
        entityManager.clear();

        assertThat(repositorio.buscarPorTelefone(TELEFONE).orElseThrow().getTentativas())
                .as("sem isto, cinco tentativas erradas seguidas continuam sendo a primeira")
                .isEqualTo(1);
    }

    @Test
    void remover_apaga_e_telefone_sem_codigo_devolve_vazio() {
        repositorio.substituir(CodigoDeVerificacao.novo(TELEFONE, "111111", agora()));
        entityManager.flush();

        repositorio.removerDe(TELEFONE);
        entityManager.flush();
        entityManager.clear();

        assertThat(repositorio.buscarPorTelefone(TELEFONE)).isEmpty();
        assertThat(repositorio.buscarPorTelefone(Telefone.de("11955556666"))).isEmpty();
    }
}
