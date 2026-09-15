package com.deliveryplatform.merchant.infrastructure.persistence.mapper;

import com.deliveryplatform.merchant.domain.model.Convite;
import com.deliveryplatform.merchant.domain.model.Telefone;
import com.deliveryplatform.merchant.infrastructure.persistence.entity.ConviteJpaEntity;
import org.springframework.stereotype.Component;

/** Escrito à mão, pelo mesmo motivo dos outros: a volta do banco valida. */
@Component
public class ConviteJpaMapper {

    public ConviteJpaEntity paraEntidade(Convite convite) {
        return new ConviteJpaEntity(
                convite.getId(),
                convite.getEstabelecimentoId(),
                convite.getTelefone().numero(),
                convite.getToken(),
                convite.getConvidadoPor(),
                convite.getEstado(),
                convite.getPermissoesOferecidas(),
                convite.getCriadoEm(),
                convite.getExpiraEm(),
                convite.getAceitoEm());
    }

    public Convite paraDominio(ConviteJpaEntity entidade) {
        return Convite.reconstituir(
                entidade.getId(),
                entidade.getEstabelecimentoId(),
                Telefone.de(entidade.getTelefone()),
                entidade.getToken(),
                entidade.getPermissoes(),
                entidade.getConvidadoPor(),
                entidade.getEstado(),
                entidade.getCriadoEm(),
                entidade.getExpiraEm(),
                entidade.getAceitoEm());
    }
}
