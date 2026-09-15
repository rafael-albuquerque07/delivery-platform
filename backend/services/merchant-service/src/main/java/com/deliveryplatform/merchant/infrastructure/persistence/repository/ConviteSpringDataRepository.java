package com.deliveryplatform.merchant.infrastructure.persistence.repository;

import com.deliveryplatform.merchant.domain.model.EstadoDoConvite;
import com.deliveryplatform.merchant.infrastructure.persistence.entity.ConviteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Pacote-privada, como as outras: quem sai do pacote é a porta. */
interface ConviteSpringDataRepository extends JpaRepository<ConviteJpaEntity, UUID> {

    Optional<ConviteJpaEntity> findByToken(String token);

    List<ConviteJpaEntity> findByEstabelecimentoIdAndEstado(
            UUID estabelecimentoId, EstadoDoConvite estado);
}
