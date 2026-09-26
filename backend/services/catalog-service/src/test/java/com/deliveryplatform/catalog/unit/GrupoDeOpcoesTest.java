package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As duas perguntas do grupo — <b>pode ser satisfeito algum dia</b> e
 * <b>pode ser satisfeito hoje</b> — e por que confundi-las despublica o
 * cardápio sozinho.
 */
class GrupoDeOpcoesTest {

    @Nested
    class Limites {

        @Test
        void c3_min_maior_que_max() {
            assertThatThrownBy(() -> GrupoDeOpcoes.novo("Tamanho", 2, 1, 0,
                    List.of(Opcao.nova("P", Precos.zero(), 0))))
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("C3")
                    .hasMessageContaining("minEscolhas maior que maxEscolhas");
        }

        @Test
        void c3_min_negativo() {
            assertThatThrownBy(() -> GrupoDeOpcoes.novo("Tamanho", -1, 1, 0, List.of()))
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("C3")
                    .hasMessageContaining("minEscolhas negativo");
        }

        @Test
        @DisplayName("max zero é grupo onde nada pode ser escolhido — não é grupo")
        void max_zero() {
            assertThatThrownBy(() -> GrupoDeOpcoes.novo("Tamanho", 0, 0, 0, List.of()))
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("maxEscolhas");
        }

        @Test
        @DisplayName("C4: 'até 3' com 2 cadastradas é teto inalcançável — o grupo nasce, a publicação recusa")
        void c4_teto_maior_que_o_numero_de_opcoes_nao_e_alcancavel() {
            GrupoDeOpcoes g = GrupoDeOpcoes.novo("Adicionais", 0, 3, 1, List.of(
                    Opcao.nova("Bacon", Precos.reais("6.00"), 0),
                    Opcao.nova("Sem cebola", Precos.reais("-2.00"), 1)));

            assertThat(g.tetoAlcancavel())
                    .as("o grupo pode existir assim em rascunho — o §2 deixa o cadastro "
                            + "pela metade —, mas o teto promete uma escolha que não existe")
                    .isFalse();
            assertThat(ProdutoDeTeste.adicionais(true).tetoAlcancavel()).isTrue();
        }

        @Test
        void nome_em_branco() {
            assertThatThrownBy(() -> GrupoDeOpcoes.novo("  ", 0, 1, 0, List.of()))
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("nome do grupo");
        }
    }

    @Nested
    class AsDuasPerguntas {

        @Test
        @DisplayName("obrigatório sem nenhuma opção: nunca satisfazível — trava publicação")
        void c5_estruturalmente_impossivel() {
            GrupoDeOpcoes g = ProdutoDeTeste.bordaSemNenhumaBorda();

            assertThat(g.obrigatorio()).isTrue();
            assertThat(g.estruturalmenteSatisfazivel()).isFalse();
            assertThat(g.satisfazivelHoje()).isFalse();
        }

        @Test
        @DisplayName("obrigatório com tudo esgotado: cadastro perfeito, dia ruim")
        void estrutura_boa_e_dia_ruim() {
            GrupoDeOpcoes g = ProdutoDeTeste.tamanho(0);

            assertThat(g.estruturalmenteSatisfazivel())
                    .as("as três opções existem — amanhã elas voltam")
                    .isTrue();
            assertThat(g.satisfazivelHoje()).isFalse();
        }

        @Test
        void obrigatorio_com_uma_de_pe() {
            GrupoDeOpcoes g = ProdutoDeTeste.tamanho(1);

            assertThat(g.contarDisponiveis()).isEqualTo(1);
            assertThat(g.satisfazivelHoje()).isTrue();
        }

        @Test
        @DisplayName("grupo opcional é sempre satisfazível, inclusive vazio")
        void opcional_vazio() {
            GrupoDeOpcoes g = GrupoDeOpcoes.novo("Adicionais", 0, 3, 0, List.of());

            assertThat(g.obrigatorio()).isFalse();
            assertThat(g.estruturalmenteSatisfazivel()).isTrue();
            assertThat(g.satisfazivelHoje()).isTrue();
        }

        @Test
        @DisplayName("min 2 com 3 opções e só 1 de pé não é satisfazível hoje")
        void min_dois() {
            GrupoDeOpcoes g = GrupoDeOpcoes.novo("Sabores", 2, 2, 0, List.of(
                    Opcao.nova("Calabresa", Precos.zero(), 0),
                    Opcao.nova("Frango", Precos.zero(), 1).com(ProdutoDeTeste.estado(false)),
                    Opcao.nova("Portuguesa", Precos.zero(), 2).com(ProdutoDeTeste.estado(false))));

            assertThat(g.estruturalmenteSatisfazivel()).isTrue();
            assertThat(g.satisfazivelHoje()).isFalse();
        }
    }

    @Test
    @DisplayName("as opções saem na ordem do comerciante, não na de cadastro")
    void opcoes_ordenadas() {
        GrupoDeOpcoes g = GrupoDeOpcoes.novo("Tamanho", 1, 1, 0, List.of(
                Opcao.nova("Grande", Precos.reais("16.00"), 2),
                Opcao.nova("Pequena", Precos.zero(), 0),
                Opcao.nova("Média", Precos.reais("8.00"), 1)));

        assertThat(g.opcoes()).extracting(Opcao::nome)
                .containsExactly("Pequena", "Média", "Grande");
    }

    @Test
    @DisplayName("a lista de opções é imutável: o grupo é um valor")
    void c7_opcoes_imutaveis() {
        GrupoDeOpcoes g = ProdutoDeTeste.tamanho(3);

        assertThatThrownBy(() -> g.opcoes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
