package com.deliveryplatform.identity.infrastructure.persistence.repository;

import com.deliveryplatform.identity.infrastructure.persistence.entity.CodigoDeVerificacaoJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface CodigoDeVerificacaoSpringDataRepository
        extends JpaRepository<CodigoDeVerificacaoJpaEntity, UUID> {

    Optional<CodigoDeVerificacaoJpaEntity> findByTelefone(String telefone);

    void deleteByTelefone(String telefone);
}
