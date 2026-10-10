package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Operacao;
import com.deliveryplatform.merchant.domain.model.TipoDeOperacao;
import com.deliveryplatform.valuetypes.Money;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A fábrica da loja recém-cadastrada (ADR-060 §2). As regras do construtor
 * canônico — M12, M15, M17 — já têm prova onde a loja inteira é montada; aqui só o
 * que a fábrica acrescenta: o zero derivado, e que ela não afrouxa a M12.
 */
class OperacaoTest {

    @Test
    void nova_deriva_minimo_zero_para_cada_modalidade_escolhida_e_desconto_zero() {
        Operacao operacao = Operacao.nova(TipoDeOperacao.SEPARACAO, Map.of(
                Modalidade.ENTREGA, Set.of(MetodoPagamento.PIX),
                Modalidade.RETIRADA, Set.of(MetodoPagamento.DINHEIRO)));

        assertThat(operacao.pedidoMinimoPorModalidade())
                .containsOnlyKeys(Modalidade.ENTREGA, Modalidade.RETIRADA);
        assertThat(operacao.pedidoMinimoDe(Modalidade.ENTREGA)).isEqualTo(Money.ZERO);
        assertThat(operacao.pedidoMinimoDe(Modalidade.RETIRADA)).isEqualTo(Money.ZERO);
        assertThat(operacao.descontoDeRetirada()).isEqualTo(Money.ZERO);
    }

    @Test
    void nova_com_mapa_vazio_ainda_e_recusada_pela_M12() {
        assertThatThrownBy(() -> Operacao.nova(TipoDeOperacao.SEPARACAO, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M12");
    }
}
