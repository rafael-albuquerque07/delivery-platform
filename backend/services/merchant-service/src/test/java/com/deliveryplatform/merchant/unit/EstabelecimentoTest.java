package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.exception.FusoHorarioInvalido;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.domain.model.Identificacao;
import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Operacao;
import com.deliveryplatform.merchant.domain.model.TipoDeOperacao;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import com.deliveryplatform.valuetypes.Money;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstabelecimentoTest {

    @Test
    void a_pizzaria_da_marli_se_constroi() {
        Estabelecimento loja = LojaDeTeste.pizzaria();

        assertThat(loja.aceita(Modalidade.ENTREGA)).isTrue();
        assertThat(loja.aceita(Modalidade.RETIRADA)).isTrue();
        assertThat(loja.metodosDe(Modalidade.RETIRADA))
                .containsExactlyInAnyOrder(MetodoPagamento.DINHEIRO, MetodoPagamento.CARTAO);
        assertThat(loja.pedidoMinimoDe(Modalidade.ENTREGA)).isEqualTo(Money.de("25.00"));
    }

    @Test
    void modalidades_aceitas_e_derivado_e_nao_campo() {
        Operacao operacao = LojaDeTeste.pizzaria().getOperacao();

        assertThat(operacao.modalidadesAceitas())
                .as("é metodosPorModalidade.keySet(), e essa é a decisão que substituiu "
                        + "o campo que existia ao lado")
                .isEqualTo(operacao.metodosPorModalidade().keySet())
                .containsExactlyInAnyOrder(Modalidade.ENTREGA, Modalidade.RETIRADA);
    }

    @Test
    void m12_loja_sem_modalidade_nenhuma_nao_opera() {
        assertThatThrownBy(() -> new Operacao(
                TipoDeOperacao.PRODUCAO, Map.of(), Money.ZERO, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M12");
    }

    @Test
    void m12_modalidade_aceita_sem_metodo_de_pagamento_e_invalida() {
        assertThatThrownBy(() -> new Operacao(
                TipoDeOperacao.PRODUCAO,
                Map.of(Modalidade.ENTREGA, Set.of()),
                Money.ZERO,
                Map.of(Modalidade.ENTREGA, Money.ZERO)))
                .as("aceitar entrega e não aceitar forma nenhuma de pagar é pedido "
                        + "que entra e não fecha")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M12");
    }

    @Test
    void m15_desconto_de_retirada_negativo_vira_acrescimo_silencioso() {
        assertThatThrownBy(() -> new Operacao(
                TipoDeOperacao.PRODUCAO,
                Map.of(Modalidade.RETIRADA, Set.of(MetodoPagamento.DINHEIRO)),
                Money.de("-0.01"),
                Map.of(Modalidade.RETIRADA, Money.ZERO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("descontoDeRetirada");
    }

    @Test
    void m17_modalidade_aceita_sem_minimo_e_invalida() {
        assertThatThrownBy(() -> new Operacao(
                TipoDeOperacao.PRODUCAO,
                Map.of(Modalidade.ENTREGA, Set.of(MetodoPagamento.PIX)),
                Money.ZERO,
                Map.of()))
                .as("ausência seria confundida com zero — o mesmo erro que M11 nomeia "
                        + "em 'ausência de área ≠ taxa zero'")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M17");
    }

    @Test
    void m17_minimo_para_modalidade_que_a_loja_nao_aceita_e_invalido() {
        assertThatThrownBy(() -> new Operacao(
                TipoDeOperacao.PRODUCAO,
                Map.of(Modalidade.RETIRADA, Set.of(MetodoPagamento.DINHEIRO)),
                Money.ZERO,
                Map.of(
                        Modalidade.RETIRADA, Money.ZERO,
                        Modalidade.ENTREGA, Money.de("25.00"))))
                .as("configuração morta que vira ativa no dia em que alguém aceitar "
                        + "a modalidade")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M17");
    }

    @Test
    void m17_minimo_negativo_nao_significa_nada() {
        assertThatThrownBy(() -> new Operacao(
                TipoDeOperacao.PRODUCAO,
                Map.of(Modalidade.ENTREGA, Set.of(MetodoPagamento.PIX)),
                Money.ZERO,
                Map.of(Modalidade.ENTREGA, Money.de("-1.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M17");
    }

    @Test
    void minimo_zero_e_valido_e_significa_sem_minimo() {
        Operacao operacao = new Operacao(
                TipoDeOperacao.SEPARACAO,
                Map.of(Modalidade.RETIRADA, Set.of(MetodoPagamento.DINHEIRO)),
                Money.ZERO,
                Map.of(Modalidade.RETIRADA, Money.ZERO));

        assertThat(operacao.pedidoMinimoDe(Modalidade.RETIRADA).ehZero()).isTrue();
    }

    @Test
    void as_colecoes_recebidas_sao_copiadas_nos_dois_niveis() {
        Set<MetodoPagamento> metodos = new HashSet<>(Set.of(MetodoPagamento.DINHEIRO));
        Map<Modalidade, Set<MetodoPagamento>> matriz = new EnumMap<>(Modalidade.class);
        matriz.put(Modalidade.RETIRADA, metodos);

        Operacao operacao = new Operacao(
                TipoDeOperacao.PRODUCAO, matriz, Money.ZERO, Map.of(Modalidade.RETIRADA, Money.ZERO));

        metodos.add(MetodoPagamento.PIX);
        matriz.put(Modalidade.ENTREGA, Set.of(MetodoPagamento.PIX));

        assertThat(operacao.metodosDe(Modalidade.RETIRADA))
                .as("Map.copyOf sozinho congelaria o mapa e deixaria os conjuntos "
                        + "de dentro mutáveis")
                .containsExactly(MetodoPagamento.DINHEIRO);
        assertThat(operacao.aceita(Modalidade.ENTREGA)).isFalse();
    }

    @Test
    void perguntar_minimo_de_modalidade_nao_aceita_estoura_em_vez_de_devolver_zero() {
        Operacao operacao = new Operacao(
                TipoDeOperacao.PRODUCAO,
                Map.of(Modalidade.RETIRADA, Set.of(MetodoPagamento.DINHEIRO)),
                Money.ZERO,
                Map.of(Modalidade.RETIRADA, Money.ZERO));

        assertThatThrownBy(() -> operacao.pedidoMinimoDe(Modalidade.ENTREGA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void m16_identificacao_exige_fuso_e_recusa_zona_de_fora() {
        assertThatThrownBy(() -> LojaDeTeste.identificacao(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> FusoHorario.de("Europe/Lisbon"))
                .isInstanceOf(FusoHorarioInvalido.class);
    }

    @Test
    void nome_endereco_e_bairro_sao_obrigatorios() {
        assertThatThrownBy(() -> identificacaoCom(" ", "Rua A, 100", "Boa Viagem"))
                .as("nome vazio")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> identificacaoCom("Pizzaria", "", "Boa Viagem"))
                .as("endereço vazio")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> identificacaoCom("Pizzaria", "Rua A, 100", null))
                .as("bairro ausente")
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Identificacao identificacaoCom(String nome, String endereco, String bairro) {
        return new Identificacao(
                nome,
                LojaDeTeste.documento(),
                LojaDeTeste.telefone(),
                endereco,
                bairro,
                FusoHorario.PADRAO);
    }

    @Test
    void to_string_nao_mostra_documento_telefone_nem_endereco() {
        String texto = LojaDeTeste.pizzaria().toString();

        assertThat(texto)
                .contains("Pizzaria da Marli")
                .doesNotContain("12345678000195")
                .doesNotContain("+5511987654321")
                .doesNotContain("Rua das Palmeiras");
    }

}
