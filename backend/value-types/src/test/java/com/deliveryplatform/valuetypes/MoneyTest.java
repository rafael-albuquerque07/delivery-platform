package com.deliveryplatform.valuetypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency DOLAR = Currency.getInstance("USD");

    @Test
    void a_escala_e_sempre_dois() {
        assertThat(Money.de("10").valor()).isEqualByComparingTo("10.00");
        assertThat(Money.de("10").valor().scale()).isEqualTo(2);
    }

    @Test
    void arredonda_half_up_e_nao_trunca() {
        assertThat(Money.de("2.005").valor()).isEqualByComparingTo("2.01");
        assertThat(Money.de("2.004").valor()).isEqualByComparingTo("2.00");
    }

    @Test
    void escalas_diferentes_do_mesmo_valor_sao_iguais() {
        assertThat(Money.de("10.0"))
                .as("record usa BigDecimal.equals, que compara escala — normalizar no "
                        + "construtor é o que impede 10.0 e 10.00 serem valores diferentes")
                .isEqualTo(Money.de("10.00"));
        assertThat(Money.de("10.0")).hasSameHashCodeAs(Money.de("10.00"));
    }

    @Test
    void soma_subtrai_e_multiplica_por_quantidade() {
        assertThat(Money.de("48.00").mais(Money.de("7.00"))).isEqualTo(Money.de("55.00"));
        assertThat(Money.de("48.00").menos(Money.de("5.00"))).isEqualTo(Money.de("43.00"));
        assertThat(Money.de("12.50").vezes(3)).isEqualTo(Money.de("37.50"));
    }

    @Test
    void o_exemplo_da_adr_024_fecha() {
        Money itens = Money.de("48.00");

        Money entrega = itens.mais(Money.de("7.00"));
        Money retirada = itens.menos(Money.de("5.00"));

        assertThat(entrega).isEqualTo(Money.de("55.00"));
        assertThat(retirada).isEqualTo(Money.de("43.00"));
        assertThat(entrega.menos(retirada)).isEqualTo(Money.de("12.00"));
    }

    @Test
    void negativo_e_permitido_porque_ajuste_delta_e_negativo() {
        Money delta = Money.de("-3.50");

        assertThat(delta.ehNegativo())
                .as("a ADR-009 exige componentes do Pedido >= 0, mas Ajuste.delta é Money "
                        + "e pode ser negativo — validar sinal aqui quebraria metade do modelo")
                .isTrue();
        assertThat(Money.de("10.00").mais(delta)).isEqualTo(Money.de("6.50"));
    }

    @Test
    void zero_e_valor_valido_e_nao_ausencia() {
        assertThat(Money.ZERO.ehZero()).isTrue();
        assertThat(Money.ZERO).isEqualTo(Money.de("0.00"));
        assertThat(Money.ZERO.ehNegativo()).isFalse();
    }

    @Test
    void moedas_diferentes_nao_se_somam() {
        Money real = Money.de("10.00");
        Money dolar = new Money(new BigDecimal("10.00"), DOLAR);

        assertThatThrownBy(() -> real.mais(dolar))
                .as("a moeda existe para isto: guarda, não recurso de multimoeda")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BRL")
                .hasMessageContaining("USD");

        assertThatThrownBy(() -> real.menos(dolar)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> real.compareTo(dolar)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void o_mesmo_valor_em_moedas_diferentes_nao_e_o_mesmo_dinheiro() {
        assertThat(Money.de("10.00")).isNotEqualTo(new Money(new BigDecimal("10.00"), DOLAR));
    }

    @Test
    void compara_pela_grandeza() {
        assertThat(Money.de("25.00").maiorQue(Money.de("22.00"))).isTrue();
        assertThat(Money.de("22.00").menorQue(Money.de("25.00"))).isTrue();
        assertThat(Money.de("25.00").maiorOuIgualA(Money.de("25.00"))).isTrue();
    }

    @Test
    void nulo_nao_vira_dinheiro() {
        assertThatThrownBy(() -> Money.de((String) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(null, Money.REAL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void toString_carrega_a_moeda() {
        assertThat(Money.de("25.00").toString())
                .as("valor sem moeda em log é número que alguém interpreta errado")
                .isEqualTo("BRL 25.00");
    }
}
