package com.deliveryplatform.identity.integration;

import com.deliveryplatform.identity.application.port.out.UsuarioRepositorio;
import com.deliveryplatform.identity.domain.model.Email;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.domain.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Primeiro Testcontainers do projeto. Sem H2 (ADR-014): índice único, CHECK
 * e TIMESTAMPTZ são comportamento do PostgreSQL, não algo que um banco em
 * memória reproduza com fidelidade.
 *
 * <p>{@code @SpringBootTest}, não {@code @DataJpaTest}: no Spring Boot 4.1.1
 * o slice de teste de JPA (e {@code TestEntityManager},
 * {@code @AutoConfigureTestDatabase}) saiu de
 * {@code spring-boot-test-autoconfigure} — o jar só traz {@code jdbc} e
 * {@code json} agora. Contexto completo com {@code webEnvironment = NONE}
 * evita subir servidor embutido; a autoconfiguração de Resource Server é
 * condicionada a contexto web e não entra em jogo aqui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
class UsuarioRepositorioJpaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private UsuarioRepositorio repositorio;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void salva_e_recupera_preservando_os_value_objects() {
        Usuario usuario = Usuario.novo("Marli", Telefone.de("11 98765-4321"), Instant.now(), "hash-1");
        usuario.adicionarEmail(new Email("Marli@Example.com"));

        Usuario salvo = repositorio.salvar(usuario);
        entityManager.flush();
        entityManager.clear();

        Optional<Usuario> recuperado = repositorio.buscarPorId(salvo.getId());

        assertThat(recuperado).isPresent();
        assertThat(recuperado.get().getTelefone()).isEqualTo(Telefone.de("+5511987654321"));
        assertThat(recuperado.get().getEmail()).isEqualTo(new Email("marli@example.com"));
        assertThat(recuperado.get().telefoneVerificado()).isTrue();
        assertThat(recuperado.get().emailVerificado()).isFalse();
    }

    @Test
    void migration_e_entidade_concordam_sobre_email_ausente() {
        Usuario usuario = Usuario.novo("Sem Email", Telefone.de("+5511911112222"), Instant.now(), "hash-2");

        Usuario salvo = repositorio.salvar(usuario);
        entityManager.flush();

        assertThat(repositorio.buscarPorId(salvo.getId())).isPresent();
    }

    @Test
    void telefone_repetido_viola_a_unicidade_de_u1() {
        Telefone telefone = Telefone.de("+5511933334444");
        repositorio.salvar(Usuario.novo("Primeiro", telefone, Instant.now(), "hash-a"));
        entityManager.flush();

        // uq_usuario_telefone está no V1__cria_usuario.sql e é nosso; o
        // invólucro da exceção é do framework e muda de versão. Em produção
        // esse erro chega como DataIntegrityViolationException, traduzido no
        // commit da transação — aqui não, porque o flush() é chamado direto
        // no EntityManager, fora do proxy do @Repository.
        assertThatThrownBy(() -> {
            repositorio.salvar(Usuario.novo("Segundo", telefone, Instant.now(), "hash-b"));
            entityManager.flush();
        })
                .as("a unicidade tem de vir da constraint que a migration criou, não de checagem em Java")
                .hasStackTraceContaining("uq_usuario_telefone");
    }

    @Test
    void dois_usuarios_sem_email_nao_violam_a_unicidade_parcial_de_u2() {
        repositorio.salvar(Usuario.novo("Um", Telefone.de("+5511955556666"), Instant.now(), "hash-c"));
        entityManager.flush();

        // Não deve lançar: NULL não colide com NULL no índice parcial.
        repositorio.salvar(Usuario.novo("Dois", Telefone.de("+5511977778888"), Instant.now(), "hash-d"));
        entityManager.flush();
    }

    @Test
    void existe_com_telefone_reflete_o_que_foi_salvo() {
        Telefone telefone = Telefone.de("+5511999998888");
        assertThat(repositorio.existeComTelefone(telefone)).isFalse();

        repositorio.salvar(Usuario.novo("Alguém", telefone, Instant.now(), "hash-e"));
        entityManager.flush();

        assertThat(repositorio.existeComTelefone(telefone)).isTrue();
    }
}
