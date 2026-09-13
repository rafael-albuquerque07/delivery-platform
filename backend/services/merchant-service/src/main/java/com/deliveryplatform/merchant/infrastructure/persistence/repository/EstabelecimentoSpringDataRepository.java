package com.deliveryplatform.merchant.infrastructure.persistence.repository;

import com.deliveryplatform.merchant.infrastructure.persistence.entity.EstabelecimentoJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Pacote-privada, como a do {@code identity-service}: quem sai do pacote é a
 * porta {@code EstabelecimentoRepositorio}, e um {@code JpaRepository} exposto
 * seria um segundo caminho para o banco, sem passar pelo mapper.
 */
interface EstabelecimentoSpringDataRepository extends JpaRepository<EstabelecimentoJpaEntity, UUID> {
}
