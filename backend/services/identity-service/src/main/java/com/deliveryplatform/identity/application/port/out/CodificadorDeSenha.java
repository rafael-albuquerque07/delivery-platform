package com.deliveryplatform.identity.application.port.out;

/**
 * A senha só existe como hash (ADR-013 §1, invariante U5).
 *
 * <p>É porta e não uso direto do {@code PasswordEncoder} do Spring pelo mesmo
 * motivo do {@code UsuarioRepositorio}: o caso de uso testa contra um duplo em
 * memória, sem subir contexto e sem pagar o custo de um bcrypt por asserção.
 */
public interface CodificadorDeSenha {

    String codificar(String senhaEmClaro);

    boolean confere(String senhaEmClaro, String hashGuardado);
}
