package com.deliveryplatform.identity.infrastructure.persistence.repository;

import com.deliveryplatform.identity.infrastructure.persistence.entity.UsuarioJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UsuarioSpringDataRepository extends JpaRepository<UsuarioJpaEntity, UUID> {

    Optional<UsuarioJpaEntity> findByTelefone(String telefone);

    boolean existsByTelefone(String telefone);
}
