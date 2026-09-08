package com.deliveryplatform.identity.application.exception;

/**
 * Única saída de erro do login, para <b>todos</b> os casos: telefone
 * inexistente, senha errada, telefone impossível de normalizar.
 *
 * <p>Não carrega qual das três foi, e não deve carregar. Resposta diferente por
 * caso transforma o login em verificador de cadastro — quem tem conta aqui e
 * quem não tem —, e o identificador sendo um telefone torna a varredura barata.
 * É a mesma regra que a ADR-011 aplica ao 403.
 */
public class CredenciaisInvalidas extends RuntimeException {

    public CredenciaisInvalidas() {
        super("telefone ou senha inválidos");
    }
}
