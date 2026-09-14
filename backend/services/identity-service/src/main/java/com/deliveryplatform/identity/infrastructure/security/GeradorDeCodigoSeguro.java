package com.deliveryplatform.identity.infrastructure.security;

import com.deliveryplatform.identity.application.port.out.GeradorDeCodigo;
import com.deliveryplatform.identity.domain.model.CodigoDeVerificacao;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Seis dígitos de {@link SecureRandom}.
 *
 * <p>{@code Random} comum é gerador linear com semente de 48 bits: quem vê dois
 * valores prevê o terceiro, e um código previsível não prova posse de telefone
 * nenhum. A diferença de custo entre os dois é irrelevante nesta frequência.
 *
 * <p>O zero à esquerda é a razão de o código ser texto e não número em lugar
 * nenhum deste fluxo: {@code 004291} é um código válido, e como número ele
 * vira 4291. É o mesmo argumento do CEP na {@code FaixaDeCep} do merchant.
 */
@Component
public class GeradorDeCodigoSeguro implements GeradorDeCodigo {

    private static final int LIMITE = (int) Math.pow(10, CodigoDeVerificacao.DIGITOS);
    private static final String FORMATO = "%0" + CodigoDeVerificacao.DIGITOS + "d";

    private final SecureRandom aleatorio = new SecureRandom();

    @Override
    public String gerar() {
        return FORMATO.formatted(aleatorio.nextInt(LIMITE));
    }
}
