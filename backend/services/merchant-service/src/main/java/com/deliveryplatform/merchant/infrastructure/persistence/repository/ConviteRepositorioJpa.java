package com.deliveryplatform.merchant.infrastructure.persistence.repository;

import com.deliveryplatform.merchant.application.port.out.ConviteRepositorio;
import com.deliveryplatform.merchant.domain.model.Convite;
import com.deliveryplatform.merchant.domain.model.EstadoDoConvite;
import com.deliveryplatform.merchant.infrastructure.persistence.mapper.ConviteJpaMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConviteRepositorioJpa implements ConviteRepositorio {

    private final ConviteSpringDataRepository springDataRepository;
    private final ConviteJpaMapper mapper;

    public ConviteRepositorioJpa(
            ConviteSpringDataRepository springDataRepository, ConviteJpaMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Convite salvar(Convite convite) {
        var salvo = springDataRepository.save(mapper.paraEntidade(convite));
        return mapper.paraDominio(salvo);
    }

    @Override
    public Optional<Convite> buscarPorToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return springDataRepository.findByToken(token).map(mapper::paraDominio);
    }

    @Override
    public List<Convite> pendentesDe(UUID estabelecimentoId) {
        return springDataRepository
                .findByEstabelecimentoIdAndEstado(estabelecimentoId, EstadoDoConvite.PENDENTE)
                .stream()
                .map(mapper::paraDominio)
                .toList();
    }
}
