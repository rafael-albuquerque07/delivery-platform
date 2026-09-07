package com.deliveryplatform.identity.infrastructure.persistence.mapper;

import com.deliveryplatform.identity.domain.model.Email;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.domain.model.Usuario;
import com.deliveryplatform.identity.infrastructure.persistence.entity.UsuarioJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Escrito à mão, não MapStruct. A volta do banco passa por
 * {@code Telefone.de(...)} e {@code new Email(...)}, que validam — um
 * mapeador gerado atribuiria campo a campo e traria de volta um estado que o
 * domínio recusaria.
 */
@Component
public class UsuarioJpaMapper {

    public UsuarioJpaEntity paraEntidade(Usuario usuario) {
        Email email = usuario.getEmail();
        return new UsuarioJpaEntity(
                usuario.getId(),
                usuario.getNome(),
                usuario.getTelefone().numero(),
                usuario.getTelefoneVerificadoEm(),
                email == null ? null : email.endereco(),
                usuario.getEmailVerificadoEm(),
                usuario.getHashDaSenha());
    }

    public Usuario paraDominio(UsuarioJpaEntity entidade) {
        String enderecoEmail = entidade.getEmail();
        Email email = enderecoEmail == null ? null : new Email(enderecoEmail);
        return Usuario.reconstituir(
                entidade.getId(),
                entidade.getNome(),
                Telefone.de(entidade.getTelefone()),
                entidade.getTelefoneVerificadoEm(),
                email,
                entidade.getEmailVerificadoEm(),
                entidade.getHashDaSenha());
    }
}
