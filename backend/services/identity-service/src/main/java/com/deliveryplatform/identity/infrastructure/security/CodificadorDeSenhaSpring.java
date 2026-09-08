package com.deliveryplatform.identity.infrastructure.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.deliveryplatform.identity.application.port.out.CodificadorDeSenha;

/**
 * Adaptador do {@code DelegatingPasswordEncoder} (ADR-037 §6).
 *
 * <p>O hash sai prefixado — {@code {bcrypt}$2a$10$…} —, e é o prefixo que torna
 * literalmente verdadeira a frase do {@code usuario.md} §3: o hash carrega o
 * próprio identificador de algoritmo. Trocar o padrão para argon2 depois não
 * exige migration nem invalidar senha: o encoder lê o hash antigo pelo prefixo
 * e regrava no formato novo.
 */
@Component
public class CodificadorDeSenhaSpring implements CodificadorDeSenha {

    private final PasswordEncoder encoder;

    public CodificadorDeSenhaSpring(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String codificar(String senhaEmClaro) {
        return encoder.encode(senhaEmClaro);
    }

    @Override
    public boolean confere(String senhaEmClaro, String hashGuardado) {
        return encoder.matches(senhaEmClaro, hashGuardado);
    }
}
