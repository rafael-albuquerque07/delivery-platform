package com.deliveryplatform.identity.application.port.in;

import com.deliveryplatform.identity.application.port.out.TokenEmitido;

/**
 * O caso de uso do login (ADR-036: o telefone é o identificador).
 *
 * <p><b>Recebe o telefone em bruto, não um {@code Telefone}.</b> A normalização
 * acontece aqui dentro, e telefone impossível de normalizar é tratado como
 * credencial inválida — não como erro de formato. Se a borda normalizasse, um
 * número malformado devolveria 400 e um número válido e inexistente devolveria
 * 401, e a diferença entre as duas respostas seria informação sobre o cadastro.
 */
public interface AutenticarUsuario {

    TokenEmitido autenticar(String telefoneBruto, String senha);
}
