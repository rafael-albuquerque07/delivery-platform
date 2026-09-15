package com.deliveryplatform.merchant.domain.exception;

/**
 * O convite não serve mais: expirou, já foi aceito, foi cancelado, ou o token
 * apresentado não é o dele.
 *
 * <p><b>Uma recusa só para os quatro motivos</b>, pela mesma razão que a
 * {@code CredenciaisInvalidas} do {@code identity} não distingue as suas três:
 * quem apresenta um token errado está adivinhando, e a diferença entre
 * "expirado" e "não existe" diz a ele se acertou o token. Para quem tem o
 * convite de verdade, a distinção também não muda o que fazer — pedir outro.
 *
 * <p>A exceção <b>não carrega o token</b>. Ele é credencial de uso único, e
 * mensagem de exceção acaba em log.
 */
public class ConviteInvalido extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConviteInvalido() {
        super("convite inválido, expirado ou já utilizado");
    }
}
