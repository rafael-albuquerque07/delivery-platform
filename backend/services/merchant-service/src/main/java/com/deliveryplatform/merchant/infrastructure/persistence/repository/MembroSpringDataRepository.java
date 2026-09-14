package com.deliveryplatform.merchant.infrastructure.persistence.repository;

import com.deliveryplatform.merchant.infrastructure.persistence.entity.MembroJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pacote-privada, como as outras: quem sai do pacote é a porta
 * {@code MembroRepositorio}.
 */
interface MembroSpringDataRepository extends JpaRepository<MembroJpaEntity, UUID> {

    Optional<MembroJpaEntity> findByUsuarioIdAndEstabelecimentoId(
            UUID usuarioId, UUID estabelecimentoId);

    List<MembroJpaEntity> findByEstabelecimentoId(UUID estabelecimentoId);

    /**
     * O cadeado de A3, e ele é <b>SQL nativo de propósito</b>.
     *
     * <p>A forma idiomática seria {@code @Lock(PESSIMISTIC_WRITE)} sobre uma
     * consulta JPQL do {@code EstabelecimentoJpaEntity}. Ela não serve aqui, por
     * duas razões independentes e as duas fatais:
     *
     * <ul>
     *   <li>Aquela entidade tem <b>cinco {@code @ElementCollection} EAGER</b>.
     *       A consulta sairia com cinco {@code left join} — o produto cartesiano
     *       que a A2b mediu — só para tomar um cadeado.</li>
     *   <li>O PostgreSQL <b>recusa</b> {@code FOR UPDATE} sobre o lado anulável
     *       de um {@code LEFT JOIN}. Não é lentidão: é erro em tempo de
     *       execução, e só apareceria quando duas pessoas mexessem na equipe ao
     *       mesmo tempo.</li>
     * </ul>
     *
     * <p>Escalar e nativo não carrega entidade, não emite {@code join}, e
     * bloqueia exatamente a linha que representa a loja. Devolve vazio quando a
     * loja não existe — e aí não há equipe para alterar.
     */
    @Query(value = "select id from estabelecimento where id = :id for update", nativeQuery = true)
    Optional<UUID> travarEstabelecimento(@Param("id") UUID id);
}
