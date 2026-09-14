package com.deliveryplatform.merchant.infrastructure.persistence.mapper;

import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.infrastructure.persistence.entity.MembroJpaEntity;
import org.springframework.stereotype.Component;

/** Escrito à mão, pelo mesmo motivo do {@code EstabelecimentoJpaMapper}. */
@Component
public class MembroJpaMapper {

    public MembroJpaEntity paraEntidade(Membro membro) {
        return new MembroJpaEntity(
                membro.getId(),
                membro.getUsuarioId(),
                membro.getEstabelecimentoId(),
                membro.getPapel(),
                membro.getEstado(),
                membro.getPermissoes(),
                membro.getCriadoEm(),
                membro.getAlteradoEm());
    }

    public Membro paraDominio(MembroJpaEntity entidade) {
        return Membro.reconstituir(
                entidade.getId(),
                entidade.getUsuarioId(),
                entidade.getEstabelecimentoId(),
                entidade.getPapel(),
                entidade.getPermissoes(),
                entidade.getEstado(),
                entidade.getCriadoEm(),
                entidade.getAlteradoEm());
    }
}
