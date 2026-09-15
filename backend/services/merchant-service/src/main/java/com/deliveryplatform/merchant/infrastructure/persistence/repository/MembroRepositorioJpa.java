package com.deliveryplatform.merchant.infrastructure.persistence.repository;

import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Equipe;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.infrastructure.persistence.mapper.MembroJpaMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MembroRepositorioJpa implements MembroRepositorio {

    private final MembroSpringDataRepository springDataRepository;
    private final MembroJpaMapper mapper;

    public MembroRepositorioJpa(
            MembroSpringDataRepository springDataRepository, MembroJpaMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Membro salvar(Membro membro) {
        var salvo = springDataRepository.save(mapper.paraEntidade(membro));
        return mapper.paraDominio(salvo);
    }

    @Override
    public Optional<Membro> buscarPorUsuarioELoja(UUID usuarioId, UUID estabelecimentoId) {
        return springDataRepository
                .findByUsuarioIdAndEstabelecimentoId(usuarioId, estabelecimentoId)
                .map(mapper::paraDominio);
    }

    @Override
    public Equipe equipeDe(UUID estabelecimentoId) {
        return montar(estabelecimentoId);
    }

    /**
     * Cadeado primeiro, leitura depois — e nunca o contrário.
     *
     * <p>Ler a equipe e só então travar a loja não protege coisa nenhuma: a
     * lista já teria sido montada antes de o concorrente ser barrado, e a
     * contagem de A3 seria sobre um retrato vencido.
     *
     * <p>{@code MANDATORY} e não {@code REQUIRED}: se este método abrisse a
     * própria transação, ela fecharia ao devolver a equipe e o cadeado morreria
     * com ela — antes de qualquer alteração ser escrita. O chamador precisa já
     * estar numa transação, e falhar alto quando não estiver é melhor do que
     * devolver um cadeado que já não existe.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Equipe equipeParaAlteracao(UUID estabelecimentoId) {
        springDataRepository.travarEstabelecimento(estabelecimentoId);
        return montar(estabelecimentoId);
    }

    private Equipe montar(UUID estabelecimentoId) {
        List<Membro> membros = springDataRepository
                .findByEstabelecimentoId(estabelecimentoId)
                .stream()
                .map(mapper::paraDominio)
                .toList();

        return Equipe.de(estabelecimentoId, membros);
    }
}
