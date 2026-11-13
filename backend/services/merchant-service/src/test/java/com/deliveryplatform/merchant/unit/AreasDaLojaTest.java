package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.model.AreaDeEntrega;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.FaixaDeCep;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import com.deliveryplatform.valuetypes.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A alteração das áreas que ganhou rota na E-10 (ADR-081): a regra é do domínio — M9 e M10, no
 * construtor do {@code Estabelecimento} —, e a loja nova passa por ele.
 */
class AreasDaLojaTest {

    @Test
    @DisplayName("substitui as áreas inteiras: a que saiu não fica, e o resto da loja não muda")
    void substitui_inteiras() {
        Estabelecimento antes = LojaDeTeste.pizzaria();

        Estabelecimento depois = antes.comAreasDeEntrega(List.of(AreaDeEntrega.de("Afogados", Money.de("4.00"))));

        assertThat(depois.getAreasDeEntrega()).extracting(AreaDeEntrega::nome).containsExactly("Afogados");
        assertThat(depois.getId()).isEqualTo(antes.getId());
        assertThat(depois.getOperacao()).isEqualTo(antes.getOperacao());
        assertThat(depois.getDisponibilidade()).isEqualTo(antes.getDisponibilidade());
    }

    @Test
    @DisplayName("lista vazia é aceita: a loja não entrega em lugar nenhum, e nenhum bairro tem taxa")
    void vazia_e_aceita() {
        Estabelecimento semAreas = LojaDeTeste.pizzaria().comAreasDeEntrega(List.of());

        assertThat(semAreas.getAreasDeEntrega()).isEmpty();
        assertThat(semAreas.areaPorNome("Boa Viagem")).isEmpty();
    }

    @Test
    @DisplayName("M9: o mesmo nome sem caixa, acento e espaço duplo é recusado — também com uma desativada")
    void m9() {
        assertThatThrownBy(() -> LojaDeTeste.pizzaria().comAreasDeEntrega(List.of(
                AreaDeEntrega.de("Boa Viagem", Money.de("7.00")),
                AreaDeEntrega.de("boa  viágem", Money.de("8.00")).desativada())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M9");
    }

    @Test
    @DisplayName("M10: faixas que se cruzam entre ativas são recusadas; com uma desativada, aceitas")
    void m10() {
        AreaDeEntrega boaViagem = AreaDeEntrega.de("Boa Viagem", Money.de("7.00"),
                List.of(FaixaDeCep.de("51000-000", "51999-999")));
        AreaDeEntrega pina = AreaDeEntrega.de("Pina", Money.de("9.00"),
                List.of(FaixaDeCep.de("51500-000", "51600-000")));

        assertThatThrownBy(() -> LojaDeTeste.pizzaria().comAreasDeEntrega(List.of(boaViagem, pina)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M10");
        assertThat(LojaDeTeste.pizzaria().comAreasDeEntrega(List.of(boaViagem, pina.desativada()))
                .getAreasDeEntrega()).hasSize(2);
    }
}
