package com.deliveryplatform.identity.infrastructure.persistence.repository;

import com.deliveryplatform.identity.application.port.out.UsuarioRepositorio;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.domain.model.Usuario;
import com.deliveryplatform.identity.infrastructure.persistence.mapper.UsuarioJpaMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UsuarioRepositorioJpa implements UsuarioRepositorio {

    private final UsuarioSpringDataRepository springDataRepository;
    private final UsuarioJpaMapper mapper;

    public UsuarioRepositorioJpa(UsuarioSpringDataRepository springDataRepository, UsuarioJpaMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        var salvo = springDataRepository.save(mapper.paraEntidade(usuario));
        return mapper.paraDominio(salvo);
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        return springDataRepository.findById(id).map(mapper::paraDominio);
    }

    @Override
    public Optional<Usuario> buscarPorTelefone(Telefone telefone) {
        return springDataRepository.findByTelefone(telefone.numero()).map(mapper::paraDominio);
    }

    @Override
    public boolean existeComTelefone(Telefone telefone) {
        return springDataRepository.existsByTelefone(telefone.numero());
    }
}
