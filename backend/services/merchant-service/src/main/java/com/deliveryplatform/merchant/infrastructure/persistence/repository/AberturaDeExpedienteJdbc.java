package com.deliveryplatform.merchant.infrastructure.persistence.repository;

import com.deliveryplatform.merchant.application.port.out.AberturaDeExpedienteRepositorio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code insert … on conflict do nothing}, e mais nada.
 *
 * <p><b>Por que não há entidade JPA.</b> A tabela não tem identidade além da
 * chave natural {@code (estabelecimento_id, expediente)}, e não é lida pelo
 * caminho quente — é escrita e esquecida. Uma entidade com {@code @IdClass} só
 * para poder usar {@code JpaRepository} seria cerimônia em volta de um
 * {@code INSERT}, e ainda esconderia a cláusula que é a decisão inteira.
 *
 * <p><b>Por que {@code EntityManager} e não {@code JdbcTemplate}.</b> Mesmo
 * contexto de persistência, mesma transação, sem depender de como o
 * gerenciador de transação expõe a conexão. A linha e o evento do outbox
 * precisam commitar juntos (invariante 7), e este é o caminho em que isso é
 * verdade sem eu precisar argumentar.
 */
@Repository
public class AberturaDeExpedienteJdbc implements AberturaDeExpedienteRepositorio {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean registrar(UUID estabelecimentoId, LocalDate expediente, Instant agora) {
        // ON CONFLICT DO NOTHING devolve 0 linhas afetadas quando a chave já
        // existe. É o banco decidindo a corrida, não o código — duas instâncias
        // da varredura disputam a mesma chave primária e exatamente uma ganha.
        int linhas = em.createNativeQuery("""
                        insert into abertura_de_expediente
                               (estabelecimento_id, expediente, publicado_em)
                        values (?1, ?2, ?3)
                        on conflict do nothing
                        """)
                .setParameter(1, estabelecimentoId)
                .setParameter(2, Date.valueOf(expediente))
                .setParameter(3, Timestamp.from(agora))
                .executeUpdate();

        return linhas == 1;
    }
}
