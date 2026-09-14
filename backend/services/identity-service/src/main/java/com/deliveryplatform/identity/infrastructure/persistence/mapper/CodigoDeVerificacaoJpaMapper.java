package com.deliveryplatform.identity.infrastructure.persistence.mapper;

import com.deliveryplatform.identity.domain.model.CodigoDeVerificacao;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.infrastructure.persistence.entity.CodigoDeVerificacaoJpaEntity;
import org.springframework.stereotype.Component;

/** Escrito à mão, pelo mesmo motivo do {@code UsuarioJpaMapper}: a volta valida. */
@Component
public class CodigoDeVerificacaoJpaMapper {

    public CodigoDeVerificacaoJpaEntity paraEntidade(CodigoDeVerificacao codigo) {
        return new CodigoDeVerificacaoJpaEntity(
                codigo.getId(),
                codigo.getTelefone().numero(),
                codigo.getCodigo(),
                codigo.getCriadoEm(),
                codigo.getExpiraEm(),
                codigo.getTentativas());
    }

    public CodigoDeVerificacao paraDominio(CodigoDeVerificacaoJpaEntity entidade) {
        return CodigoDeVerificacao.reconstituir(
                entidade.getId(),
                Telefone.de(entidade.getTelefone()),
                entidade.getCodigo(),
                entidade.getCriadoEm(),
                entidade.getExpiraEm(),
                entidade.getTentativas());
    }
}
