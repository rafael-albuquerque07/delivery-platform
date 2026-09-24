package com.deliveryplatform.merchant.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxSpringDataRepository extends JpaRepository<OutboxJpaEntity, UUID> {

    /**
     * Pega um lote de pendentes e <b>trava as linhas pulando as já travadas</b>.
     *
     * <p><b>Por que SQL nativo.</b> {@code SKIP LOCKED} não existe em JPQL — o
     * {@code @Lock(PESSIMISTIC_WRITE)} do Spring Data gera {@code FOR UPDATE} e
     * nada mais. É o mesmo motivo pelo qual o cadeado da B1 é consulta nativa,
     * por uma razão diferente: lá o PostgreSQL recusava {@code FOR UPDATE} sobre
     * o lado anulável de um {@code LEFT JOIN}; aqui a cláusula simplesmente não
     * tem como ser escrita em JPQL. A conclusão é a mesma nas duas vezes: trava
     * de linha se escreve em SQL.
     *
     * <p><b>Por que {@code SKIP LOCKED} e não só {@code FOR UPDATE}.</b> Sem ele,
     * duas instâncias do serviço disputam as mesmas linhas e a segunda espera a
     * primeira: o relay para de escalar exatamente quando há tráfego para
     * escalar. Com ele, cada instância leva um lote diferente e nenhuma bloqueia
     * a outra.
     *
     * <p>A ordem por {@code ocorrido_em} é a ordem em que o relay <b>tenta</b>
     * publicar, não a de chegada: cada envio pega um canal do cache, e canais
     * diferentes não são ordenados entre si pelo broker. É justamente por isso
     * que o payload do evento é estado e não delta, e que o contrato manda o
     * consumidor descartar evento mais velho (ADR-043 §4).
     */
    @Query(value = """
            select * from outbox
             where publicado_em is null
             order by ocorrido_em
             limit :lote
               for update skip locked
            """, nativeQuery = true)
    List<OutboxJpaEntity> travarLotePendente(@Param("lote") int lote);

    long countByPublicadoEmIsNull();
}
