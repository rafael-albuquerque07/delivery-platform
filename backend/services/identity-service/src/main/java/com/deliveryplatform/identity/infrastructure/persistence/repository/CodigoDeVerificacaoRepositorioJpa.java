package com.deliveryplatform.identity.infrastructure.persistence.repository;

import com.deliveryplatform.identity.application.port.out.CodigoDeVerificacaoRepositorio;
import com.deliveryplatform.identity.domain.model.CodigoDeVerificacao;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.infrastructure.persistence.mapper.CodigoDeVerificacaoJpaMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class CodigoDeVerificacaoRepositorioJpa implements CodigoDeVerificacaoRepositorio {

    private final CodigoDeVerificacaoSpringDataRepository springDataRepository;
    private final CodigoDeVerificacaoJpaMapper mapper;

    public CodigoDeVerificacaoRepositorioJpa(
            CodigoDeVerificacaoSpringDataRepository springDataRepository,
            CodigoDeVerificacaoJpaMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    /**
     * Apaga o anterior e grava o novo, nessa ordem — e o {@code flush} entre os
     * dois não é zelo.
     *
     * <p>O Hibernate ordena as operações da sessão por tipo, e o
     * {@code INSERT} sai <b>antes</b> do {@code DELETE}. Sem o flush explícito,
     * o segundo pedido de código para o mesmo telefone bate no
     * {@code UNIQUE (telefone)} e volta como erro de banco — um defeito que só
     * aparece no segundo pedido, que é o caso que ninguém testa à mão.
     */
    @Override
    @Transactional
    public CodigoDeVerificacao substituir(CodigoDeVerificacao codigo) {
        springDataRepository.deleteByTelefone(codigo.getTelefone().numero());
        springDataRepository.flush();

        var salvo = springDataRepository.save(mapper.paraEntidade(codigo));
        return mapper.paraDominio(salvo);
    }

    @Override
    public Optional<CodigoDeVerificacao> buscarPorTelefone(Telefone telefone) {
        return springDataRepository.findByTelefone(telefone.numero()).map(mapper::paraDominio);
    }

    @Override
    @Transactional
    public void removerDe(Telefone telefone) {
        springDataRepository.deleteByTelefone(telefone.numero());
    }
}
