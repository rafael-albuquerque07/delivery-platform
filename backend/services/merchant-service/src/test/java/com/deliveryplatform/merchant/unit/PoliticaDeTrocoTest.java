package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.model.PoliticaDeTroco;
import com.deliveryplatform.valuetypes.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Também é o teste que prova que o {@code :value-types} chegou ao classpath de
 * um serviço — a consequência negativa que a ADR-040 assumiu com prazo de uma
 * rodada.
 */
class PoliticaDeTrocoTest {

    @Test
    void fundo_positivo_e_valido() {
        PoliticaDeTroco politica = new PoliticaDeTroco(Money.de("50.00"), false);

        assertThat(politica.fundoMaximoDeTroco()).isEqualTo(Money.de("50.00"));
        assertThat(politica.aceitaPedidoSemTrocoDisponivel()).isFalse();
    }

    @Test
    void zero_e_valido_e_significa_loja_sem_fundo_de_troco() {
        PoliticaDeTroco politica = new PoliticaDeTroco(Money.ZERO, false);

        assertThat(politica.fundoMaximoDeTroco().ehZero())
                .as("zero é configuração, não ausência de configuração — igual ao "
                        + "pedido mínimo zero de estabelecimento.md §4")
                .isTrue();
    }

    @Test
    void fundo_negativo_e_recusado() {
        assertThatThrownBy(() -> new PoliticaDeTroco(Money.de("-1.00"), false))
                .as("dívida de troco não existe")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fundo_nulo_e_recusado() {
        assertThatThrownBy(() -> new PoliticaDeTroco(null, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void o_exemplo_do_estabelecimento_md_fecha_com_a_conta_do_lado_de_fora() {
        PoliticaDeTroco politica = new PoliticaDeTroco(Money.de("50.00"), false);

        Money total = Money.de("43.00");
        Money trocoPara = Money.de("100.00");
        Money trocoDevido = trocoPara.menos(total);

        assertThat(trocoDevido).isEqualTo(Money.de("57.00"));
        assertThat(trocoDevido.maiorQue(politica.fundoMaximoDeTroco()))
                .as("a conta é do order-service no fechamento do pedido; aqui ela "
                        + "aparece no teste justamente porque não existe como método")
                .isTrue();
        assertThat(politica.aceitaPedidoSemTrocoDisponivel())
                .as("com a política assim, o pedido é RECUSADO dizendo o número: "
                        + "\"o máximo hoje é R$ 50,00\"")
                .isFalse();
    }
}
