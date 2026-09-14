package com.deliveryplatform.identity.application.port.in;

/**
 * Primeiro passo do cadastro (ADR-042 §1).
 *
 * <p><b>Não devolve nada, e é assim de propósito.</b> O código não volta na
 * resposta — ele é entregue pelo operador, que o lê da tabela. Devolvê-lo aqui
 * transformaria a verificação em formalidade: qualquer um provaria posse de
 * qualquer número lendo a própria resposta.
 *
 * <p>Também não diz se o telefone já tem conta. Telefone cadastrado recebe a
 * mesma resposta e não gera código nenhum — ADR-042 §5.
 */
public interface SolicitarCodigoDeVerificacao {

    void solicitar(String telefoneBruto);
}
