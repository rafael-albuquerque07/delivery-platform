package com.deliveryplatform.merchant.infrastructure.persistence.repository;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.infrastructure.persistence.mapper.EstabelecimentoJpaMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class EstabelecimentoRepositorioJpa implements EstabelecimentoRepositorio {

    private final EstabelecimentoSpringDataRepository springDataRepository;
    private final EstabelecimentoJpaMapper mapper;

    public EstabelecimentoRepositorioJpa(
            EstabelecimentoSpringDataRepository springDataRepository, EstabelecimentoJpaMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Estabelecimento salvar(Estabelecimento estabelecimento) {
        var salvo = springDataRepository.save(mapper.paraEntidade(estabelecimento));
        return mapper.paraDominio(salvo);
    }

    @Override
    public Optional<Estabelecimento> buscarPorId(UUID id) {
        return springDataRepository.findById(id).map(mapper::paraDominio);
    }
}
