package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.support.Precos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpcaoTest {

    @Test
    @DisplayName("acréscimo negativo é desconto, e desconto é opção — 'sem queijo −R$ 2,00'")
    void acrescimo_negativo_e_valido() {
        Opcao semQueijo = Opcao.nova("Sem queijo", Precos.reais("-2.00"), 0);

        assertThat(semQueijo.acrescimo()).isEqualTo(Precos.reais("-2.00"));
    }

    @Test
    @DisplayName("acréscimo ausente não vira zero por conta própria")
    void acrescimo_nulo() {
        assertThatThrownBy(() -> Opcao.nova("Bacon", null, 0))
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .hasMessageContaining("use zero, não nulo");
    }

    @Test
    void nome_em_branco() {
        assertThatThrownBy(() -> Opcao.nova("   ", Precos.zero(), 0))
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .hasMessageContaining("nome da opção");
    }

    @Test
    void nasce_disponivel() {
        assertThat(Opcao.nova("Bacon", Precos.reais("6.00"), 0).disponivel()).isTrue();
    }

    @Test
    @DisplayName("mudar disponibilidade devolve outra opção com o mesmo id")
    void troca_de_disponibilidade_preserva_identidade() {
        Opcao antes = Opcao.nova("Bacon", Precos.reais("6.00"), 0);
        Opcao depois = antes.comDisponibilidade(false);

        assertThat(depois.id()).isEqualTo(antes.id());
        assertThat(depois.nome()).isEqualTo(antes.nome());
        assertThat(depois.acrescimo()).isEqualTo(antes.acrescimo());
        assertThat(depois.ordem()).isEqualTo(antes.ordem());
        assertThat(depois.disponivel()).isFalse();
    }
}
