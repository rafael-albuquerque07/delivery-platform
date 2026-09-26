package com.deliveryplatform.catalog.support;

import com.deliveryplatform.valuetypes.Money;

import java.math.BigDecimal;

/**
 * <b>Único ponto dos testes desta rodada que toca a fábrica do {@link Money}.</b>
 *
 * <p>Par do {@code exigirPrecoValido} do {@code Produto}: a rodada inteira
 * concentra em dois métodos tudo o que depende da assinatura de uma classe que
 * mora em outro módulo. Se a fábrica se chamar {@code Money.reais(...)},
 * {@code Money.of(...)} ou receber {@code String}, conserte aqui e nos oitenta
 * usos nada muda.
 *
 * <p>Vale a regra do repositório: <i>confirme a assinatura no jar resolvido,
 * não na memória</i>.
 */
public final class Precos {

    private Precos() {
    }

    public static Money reais(String valor) {
        return Money.de(new BigDecimal(valor));
    }

    public static Money zero() {
        return reais("0.00");
    }
}
