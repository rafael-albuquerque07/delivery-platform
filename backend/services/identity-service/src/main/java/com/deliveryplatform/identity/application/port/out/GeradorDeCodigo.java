package com.deliveryplatform.identity.application.port.out;

/**
 * De onde vem o número de seis dígitos.
 *
 * <p>É porta, e não um {@code Math.random()} dentro do caso de uso, por dois
 * motivos independentes: o de produção precisa ser criptograficamente seguro —
 * um código previsível não é prova de posse de nada — e o de teste precisa ser
 * previsível, ou não há como escrever "o código certo entra e o errado não".
 * Uma implementação só não atende aos dois.
 */
public interface GeradorDeCodigo {

    String gerar();
}
