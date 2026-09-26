package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.deliveryplatform.catalog.support.ProdutoDeTeste.CATEGORIA;
import static com.deliveryplatform.catalog.support.ProdutoDeTeste.LOJA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O que o produto recusa no nascimento, e o que ele aceita incompleto. */
class ProdutoTest {

    @Nested
    class OQueEleRecusa {

        @Test
        void c8_sem_estabelecimento() {
            assertThatThrownBy(() -> Produto.rascunho(null, CATEGORIA, "Pizza",
                    Precos.reais("40.00"), ModoDeControle.QUALITATIVO, 0))
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("estabelecimento");
        }

        @Test
        void sem_categoria() {
            assertThatThrownBy(() -> Produto.rascunho(LOJA, null, "Pizza",
                    Precos.reais("40.00"), ModoDeControle.QUALITATIVO, 0))
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("categoria");
        }

        @Test
        @DisplayName("nome em branco é nome ausente — três espaços não são nome")
        void nome_em_branco() {
            assertThatThrownBy(() -> Produto.rascunho(LOJA, CATEGORIA, "   ",
                    Precos.reais("40.00"), ModoDeControle.QUALITATIVO, 0))
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("nome do produto");
        }

        @Test
        void c1_preco_negativo() {
            assertThatThrownBy(() -> Produto.rascunho(LOJA, CATEGORIA, "Pizza",
                    Precos.reais("-1.00"), ModoDeControle.QUALITATIVO, 0))
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("C1")
                    .hasMessageContaining("negativo");
        }

        @Test
        void sem_preco() {
            assertThatThrownBy(() -> Produto.rascunho(LOJA, CATEGORIA, "Pizza",
                    null, ModoDeControle.QUALITATIVO, 0))
                    .isInstanceOf(RegraDoCatalogoViolada.class);
        }
    }

    @Nested
    class OQueEleAceita {

        @Test
        @DisplayName("C1: preço zero cabe no rascunho — o comerciante pode ainda não ter posto o preço")
        void c1_preco_zero_e_aceito_em_rascunho() {
            Produto semPreco = Produto.rascunho(LOJA, CATEGORIA, "Pizza do dia",
                    Precos.zero(), ModoDeControle.QUALITATIVO, 0);

            assertThat(semPreco.getPrecoBase()).isEqualTo(Precos.zero());
            assertThat(semPreco.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.RASCUNHO);
        }

        @Test
        @DisplayName("rascunho incompleto é válido: sem descrição, sem imagem, sem grupo")
        void cadastro_incompleto() {
            Produto p = ProdutoDeTeste.margherita();

            assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.RASCUNHO);
            assertThat(p.getDescricao()).isNull();
            assertThat(p.getImagemRef()).isNull();
            assertThat(p.getGruposDeOpcoes()).isEmpty();
        }

        @Test
        @DisplayName("nasce DISPONIVEL e sem carimbo — ninguém disse nada ainda")
        void nasce_disponivel_sem_carimbo() {
            var d = ProdutoDeTeste.margherita().getDisponibilidade();

            assertThat(d.estado()).isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);
            assertThat(d.marcadoEm()).isNull();
            assertThat(d.expedienteDeReferencia()).isNull();
        }

        @Test
        @DisplayName("descrição em branco vira ausente, e não parágrafo vazio na tela")
        void descricao_em_branco_vira_nula() {
            Produto p = ProdutoDeTeste.margherita();

            p.descreverCom("   ");
            assertThat(p.getDescricao()).isNull();

            p.descreverCom("  Molho, muçarela e manjericão.  ");
            assertThat(p.getDescricao()).isEqualTo("Molho, muçarela e manjericão.");
        }
    }

    @Test
    @DisplayName("a lista de grupos é cópia: ninguém mexe no agregado por fora")
    void c7_grupos_sao_copia() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(3));

        assertThatThrownBy(() -> p.getGruposDeOpcoes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("os grupos saem na ordem do comerciante, não na de inserção")
    void grupos_ordenados() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(ProdutoDeTeste.adicionais(true));   // ordem 1
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(3));         // ordem 0

        assertThat(p.getGruposDeOpcoes())
                .extracting(g -> g.nome())
                .containsExactly("Tamanho", "Adicionais");
    }

    @Test
    void o_id_nasce_e_nao_muda() {
        Produto p = ProdutoDeTeste.margherita();

        assertThat(p.getId()).isNotNull();
        assertThat(p.getEstabelecimentoId()).isEqualTo(LOJA);
        assertThat(p.getCategoriaId()).isEqualTo(CATEGORIA);
        assertThat(p.getId()).isNotEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }
}
