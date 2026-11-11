package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import com.deliveryplatform.valuetypes.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As duas alterações da operação que ganharam rota na E-9 (ADR-080): a regra é do domínio —
 * M15 e M17, no construtor do {@code Operacao} —, e a loja nova passa por ele.
 */
class ValoresDaOperacaoTest {

    private static Map<Modalidade, Money> minimos(String entrega, String retirada) {
        Map<Modalidade, Money> mapa = new EnumMap<>(Modalidade.class);
        if (entrega != null) mapa.put(Modalidade.ENTREGA, Money.de(entrega));
        if (retirada != null) mapa.put(Modalidade.RETIRADA, Money.de(retirada));
        return mapa;
    }

    @Test
    @DisplayName("substitui os mínimos inteiros, e o resto da loja fica como estava")
    void substitui_os_minimos() {
        Estabelecimento antes = LojaDeTeste.pizzaria();

        Estabelecimento depois = antes.comPedidoMinimo(minimos("30.00", "10.00"));

        assertThat(depois.pedidoMinimoDe(Modalidade.ENTREGA)).isEqualTo(Money.de("30.00"));
        assertThat(depois.pedidoMinimoDe(Modalidade.RETIRADA)).isEqualTo(Money.de("10.00"));
        assertThat(depois.getId()).isEqualTo(antes.getId());
        assertThat(depois.getOperacao().descontoDeRetirada())
                .isEqualTo(antes.getOperacao().descontoDeRetirada());
        assertThat(depois.getOperacao().metodosPorModalidade())
                .isEqualTo(antes.getOperacao().metodosPorModalidade());
    }

    /**
     * A frase da H1.2 — "com zero valendo como 'sem mínimo'" — presa a uma linha de código: o
     * zero <b>é uma entrada</b>, e a modalidade que falta não é lida como zero.
     */
    @Test
    @DisplayName("zero vale como \"sem mínimo\": é entrada, e a ausência é recusada (M17)")
    void zero_e_entrada_e_ausencia_e_recusa() {
        Estabelecimento semMinimo = LojaDeTeste.pizzaria().comPedidoMinimo(minimos("0", "0"));

        assertThat(semMinimo.getOperacao().pedidoMinimoPorModalidade())
                .containsOnlyKeys(Modalidade.ENTREGA, Modalidade.RETIRADA);
        assertThat(semMinimo.pedidoMinimoDe(Modalidade.ENTREGA)).isEqualTo(Money.ZERO);

        assertThatThrownBy(() -> LojaDeTeste.pizzaria().comPedidoMinimo(minimos("0", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M17");
    }

    @Test
    @DisplayName("mínimo negativo é recusado (M17)")
    void minimo_negativo() {
        assertThatThrownBy(() -> LojaDeTeste.pizzaria().comPedidoMinimo(minimos("-1.00", "0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M17");
    }

    @Test
    @DisplayName("troca o desconto de retirada, e zero é sem desconto")
    void troca_o_desconto() {
        assertThat(LojaDeTeste.pizzaria().comDescontoDeRetirada(Money.de("7.50"))
                .getOperacao().descontoDeRetirada()).isEqualTo(Money.de("7.50"));
        assertThat(LojaDeTeste.pizzaria().comDescontoDeRetirada(Money.ZERO)
                .getOperacao().descontoDeRetirada()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("desconto negativo é recusado (M15)")
    void desconto_negativo() {
        assertThatThrownBy(() -> LojaDeTeste.pizzaria().comDescontoDeRetirada(Money.de("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("descontoDeRetirada");
    }
}
