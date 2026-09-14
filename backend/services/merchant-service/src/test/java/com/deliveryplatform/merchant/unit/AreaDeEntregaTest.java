package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.model.AreaDeEntrega;
import com.deliveryplatform.merchant.domain.model.Disponibilidade;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.FaixaDeCep;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import com.deliveryplatform.valuetypes.Money;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AreaDeEntregaTest {

    private static final FaixaDeCep BOA_VIAGEM = FaixaDeCep.de("51000-000", "51999-999");

    // ── FaixaDeCep ──────────────────────────────────────────────────────────

    @Test
    void a_formatacao_que_o_comerciante_digita_e_descartada() {
        assertThat(BOA_VIAGEM.inicio()).isEqualTo("51000000");
        assertThat(BOA_VIAGEM.fim()).isEqualTo("51999999");
    }

    @Test
    void o_zero_a_esquerda_do_cep_sobrevive() {
        assertThat(FaixaDeCep.unica("01310-100").inicio())
                .as("01310-100 é a Avenida Paulista; como número viraria 1310100 e "
                        + "o zero só voltaria se alguém lembrasse de formatar")
                .isEqualTo("01310100");
        assertThat("01310100".compareTo("50000000"))
                .as("com largura fixa, a comparação de texto é a comparação numérica")
                .isNegative();
    }

    @Test
    void a_faixa_e_inclusiva_nas_duas_pontas() {
        assertThat(BOA_VIAGEM.contem("51000000")).isTrue();
        assertThat(BOA_VIAGEM.contem("51500-123")).isTrue();
        assertThat(BOA_VIAGEM.contem("51999999")).isTrue();
        assertThat(BOA_VIAGEM.contem("52000000")).isFalse();
    }

    @Test
    void faixa_invertida_e_recusada() {
        assertThatThrownBy(() -> FaixaDeCep.de("51999-999", "51000-000"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cep_que_nao_tem_oito_digitos_e_recusado() {
        assertThatThrownBy(() -> FaixaDeCep.unica("5100000"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FaixaDeCep.unica(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sobreposicao_nao_tem_caso_especial() {
        FaixaDeCep base = FaixaDeCep.de("50000000", "50499999");

        assertThat(base.sobrepoe(FaixaDeCep.de("50400000", "50900000")))
                .as("cruzadas").isTrue();
        assertThat(FaixaDeCep.de("50000000", "50999999").sobrepoe(FaixaDeCep.unica("50500000")))
                .as("uma contém a outra").isTrue();
        assertThat(base.sobrepoe(FaixaDeCep.de("50499999", "50999999")))
                .as("encostadas por um CEP").isTrue();
        assertThat(base.sobrepoe(FaixaDeCep.de("50500000", "50999999")))
                .as("adjacentes, sem buraco e sem sobra").isFalse();
    }

    // ── AreaDeEntrega ───────────────────────────────────────────────────────

    @Test
    void a_normalizacao_do_nome_e_regra_de_dominio() {
        assertThat(AreaDeEntrega.normalizar("boa viagem")).isEqualTo("BOA VIAGEM");
        assertThat(AreaDeEntrega.normalizar("BOA  VIAGEM ")).isEqualTo("BOA VIAGEM");
        assertThat(AreaDeEntrega.normalizar("Jardim Botânico"))
                .as("sem acento — senão a Marli cadastra a mesma área duas vezes")
                .isEqualTo("JARDIM BOTANICO");
    }

    @Test
    void taxa_zero_e_valida() {
        assertThat(AreaDeEntrega.de("Centro", Money.ZERO).taxa().ehZero()).isTrue();
    }

    @Test
    void taxa_negativa_e_recusada() {
        assertThatThrownBy(() -> AreaDeEntrega.de("Centro", Money.de("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nome_vazio_e_recusado() {
        assertThatThrownBy(() -> AreaDeEntrega.de("   ", Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void area_sem_faixa_de_cep_e_normal_e_nao_e_alcancavel_por_cep() {
        AreaDeEntrega centro = AreaDeEntrega.de("Centro", Money.ZERO);

        assertThat(centro.faixasDeCep()).isEmpty();
        assertThat(centro.cobre("51500000"))
                .as("a ADR-020 modela por bairro nomeado; o CEP é refinamento opcional")
                .isFalse();
    }

    @Test
    void faixa_repetida_na_mesma_area_e_recusada_e_as_demais_saem_ordenadas() {
        assertThatThrownBy(() ->
                AreaDeEntrega.de("Centro", Money.ZERO, List.of(BOA_VIAGEM, BOA_VIAGEM)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(AreaDeEntrega.de("Centro", Money.ZERO, List.of(
                        FaixaDeCep.de("52000000", "52999999"),
                        FaixaDeCep.de("50000000", "50999999")))
                .faixasDeCep())
                .containsExactly(
                        FaixaDeCep.de("50000000", "50999999"),
                        FaixaDeCep.de("52000000", "52999999"));
    }

    @Test
    void desativar_preserva_nome_taxa_e_faixas() {
        AreaDeEntrega desativada =
                AreaDeEntrega.de("Boa Viagem", Money.de("7.00"), List.of(BOA_VIAGEM)).desativada();

        assertThat(desativada.ativa()).isFalse();
        assertThat(desativada.taxa()).isEqualTo(Money.de("7.00"));
        assertThat(desativada.faixasDeCep()).containsExactly(BOA_VIAGEM);
    }

    // ── M9 ──────────────────────────────────────────────────────────────────

    @Test
    void m9_duas_areas_com_o_mesmo_nome_normalizado_sao_recusadas() {
        assertThatThrownBy(() -> lojaCom(
                AreaDeEntrega.de("Boa Viagem", Money.de("7.00")),
                AreaDeEntrega.de("boa  viagem", Money.de("9.00"))))
                .as("a mesma área com taxas divergentes é o defeito que E1 nomeia")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M9");
    }

    @Test
    void m9_vale_mesmo_quando_uma_delas_esta_desativada() {
        assertThatThrownBy(() -> lojaCom(
                AreaDeEntrega.de("Boa Viagem", Money.de("7.00")),
                AreaDeEntrega.de("BOA VIAGEM", Money.de("9.00")).desativada()))
                .as("desativar não desfaz a duplicata, só a esconde — o escopo de M9 é "
                        + "todas as áreas, ao contrário do de M10")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M9");
    }

    // ── M10 ─────────────────────────────────────────────────────────────────

    @Test
    void m10_faixas_cruzadas_entre_areas_ativas_sao_recusadas() {
        assertThatThrownBy(() -> lojaCom(
                AreaDeEntrega.de("Boa Viagem", Money.de("7.00"),
                        List.of(FaixaDeCep.de("50000000", "50999999"))),
                AreaDeEntrega.de("Pina", Money.de("9.00"),
                        List.of(FaixaDeCep.de("50500000", "51000000")))))
                .as("o mesmo endereço resolveria para duas taxas")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M10");
    }

    @Test
    void m10_ignora_area_desativada_porque_ela_nao_cota_nada() {
        Estabelecimento loja = lojaCom(
                AreaDeEntrega.de("Boa Viagem", Money.de("7.00"),
                        List.of(FaixaDeCep.de("50000000", "50999999"))),
                AreaDeEntrega.de("Pina", Money.de("9.00"),
                        List.of(FaixaDeCep.de("50500000", "51000000"))).desativada());

        assertThat(loja.getAreasDeEntrega()).hasSize(2);
    }

    @Test
    void m10_aceita_faixas_adjacentes() {
        Estabelecimento loja = lojaCom(
                AreaDeEntrega.de("Boa Viagem", Money.de("7.00"),
                        List.of(FaixaDeCep.de("50000000", "50499999"))),
                AreaDeEntrega.de("Pina", Money.de("9.00"),
                        List.of(FaixaDeCep.de("50500000", "50999999"))));

        assertThat(loja.getAreasDeEntrega()).hasSize(2);
    }

    // ── M11 e as duas consultas ─────────────────────────────────────────────

    @Test
    void o_caminho_normal_e_pelo_nome_do_bairro() {
        Estabelecimento loja = LojaDeTeste.pizzaria();

        assertThat(loja.areaPorNome("Boa Viagem").orElseThrow().taxa())
                .isEqualTo(Money.de("7.00"));
        assertThat(loja.areaPorNome("boa  viagem"))
                .as("a normalização é o que faz o cliente achar o bairro escrevendo "
                        + "do jeito dele")
                .isPresent();
    }

    @Test
    void m11_ausencia_de_area_devolve_vazio_e_nunca_taxa_zero() {
        Estabelecimento loja = LojaDeTeste.pizzaria();

        assertThat(loja.areaPorNome("Casa Amarela"))
                .as("entregar de graça onde a loja não entrega é o erro que M11 impede")
                .isEmpty();
        assertThat(loja.areaPara("99999-999")).isEmpty();
        assertThat(loja.areaPorNome(null)).isEmpty();
    }

    @Test
    void taxa_zero_e_area_de_verdade_e_e_achavel() {
        assertThat(LojaDeTeste.pizzaria().areaPorNome("Centro").orElseThrow().taxa().ehZero())
                .as("zero é configuração; a ausência é outra coisa")
                .isTrue();
    }

    @Test
    void area_desativada_nao_e_achada_por_nome_nem_por_cep() {
        Estabelecimento loja = LojaDeTeste.pizzaria();

        assertThat(loja.areaPorNome("Pina")).isEmpty();
        assertThat(loja.areaPara("50500-000"))
                .as("desativar afeta pedidos futuros e mais nada")
                .isEmpty();
    }

    @Test
    void o_cep_resolve_o_bairro_sem_perguntar() {
        assertThat(LojaDeTeste.pizzaria().areaPara("51500-000").orElseThrow().nome())
                .isEqualTo("Boa Viagem");
    }

    @Test
    void cep_malformado_reclama_em_vez_de_dizer_que_nao_achou() {
        assertThatThrownBy(() -> LojaDeTeste.pizzaria().areaPara("123"))
                .as("sem validar antes, uma loja sem faixa nenhuma devolveria "
                        + "\"não achei\" para uma entrada inválida")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loja_sem_area_nenhuma_e_valida() {
        Estabelecimento loja = lojaCom();

        assertThat(loja.getAreasDeEntrega()).isEmpty();
        assertThat(loja.areaPorNome("Centro")).isEmpty();
    }

    // ── fixture local ───────────────────────────────────────────────────────

    private static Estabelecimento lojaCom(AreaDeEntrega... areas) {
        return Estabelecimento.novo(
                LojaDeTeste.identificacao(FusoHorario.PADRAO),
                LojaDeTeste.operacao(),
                LojaDeTeste.troco(),
                Disponibilidade.semHorario(),
                List.of(areas));
    }
}
