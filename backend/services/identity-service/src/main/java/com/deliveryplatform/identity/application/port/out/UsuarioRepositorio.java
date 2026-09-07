package com.deliveryplatform.identity.application.port.out;

import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.domain.model.Usuario;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída para persistência do {@code Usuario} (README.md — "application
 * orquestra casos de uso e depende de interfaces (port/out)"). O adaptador
 * mora em infrastructure/persistence.
 */
public interface UsuarioRepositorio {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorId(UUID id);

    Optional<Usuario> buscarPorTelefone(Telefone telefone);

    boolean existeComTelefone(Telefone telefone);
}
