package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.domain.model.Disponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.Precos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.deliveryplatform.catalog.support.ProdutoDeTeste.CATEGORIA;
import static com.deliveryplatform.catalog.support.ProdutoDeTeste.LOJA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C2 — {@code precoUnitario > 0} para <b>toda</b> combinação válida.
 *
 * <p>A invariante só tem graça porque o acréscimo pode ser negativo. Com
 * desconto no cardápio, o menor preço de um produto não é o preço base: é o
 * preço base mais a combinação mais barata que o consumidor consegue montar sem
 * violar nenhum mínimo nem nenhum teto.
 *
 * <p>O algoritmo cabe numa frase: <b>ordene os acréscimos do grupo do menor
 * para o maior, pegue os {@code minEscolhas} primeiros porque é obrigatório, e
 * continue pegando enquanto o próximo for negativo e o teto permitir.</b> Como
 * a lista está ordenada, o primeiro acréscimo não-negativo depois do mínimo
 * encerra o grupo — dali para a frente só encarece.
 *
 * <p>A conta usa <b>todas</b> as opções, disponíveis ou não. C2 é sobre o
 * cadastro, não sobre o dia: uma combinação que hoje está esgotada é uma
 * combinação válida amanhã, e um produto que fica de graça amanhã é um defeito
 * hoje.
 */
class PrecoMinimoTest {

    private static Produto produtoDe(String precoBase) {
        return Produto.rascunho(LOJA, CATEGORIA, "Pizza", Precos.reais(precoBase),
                ModoDeControle.QUALITATIVO, 0);
    }

    @Nested
    class AConta {

        @Test
        @DisplayName("sem grupo, o mínimo é o próprio preço base")
        void sem_grupo() {
            assertThat(produtoDe("49.90").precoMinimoPossivel()).isEqualTo(Precos.reais("49.90"));
        }

        @Test
        @DisplayName("grupo obrigatório soma a escolha mais barata, mesmo positiva")
        void obrigatorio_soma_a_mais_barata() {
            Produto p = produtoDe("30.00");
            p.acrescentarGrupo(GrupoDeOpcoes.novo("Tamanho", 1, 1, 0, List.of(
                    Opcao.nova("Pequena", Precos.reais("0.00"), 0),
                    Opcao.nova("Média", Precos.reais("8.00"), 1),
                    Opcao.nova("Grande", Precos.reais("16.00"), 2))));

            assertThat(p.precoMinimoPossivel()).isEqualTo(Precos.reais("30.00"));
        }

        @Test
        @DisplayName("min 2 soma as duas mais baratas — obrigatório é obrigatório")
        void min_dois_soma_duas() {
            Produto p = produtoDe("20.00");
            p.acrescentarGrupo(GrupoDeOpcoes.novo("Sabores", 2, 2, 0, List.of(
                    Opcao.nova("Calabresa", Precos.reais("1.00"), 0),
                    Opcao.nova("Frango", Precos.reais("2.00"), 1),
                    Opcao.nova("Portuguesa", Precos.reais("3.00"), 2))));

            assertThat(p.precoMinimoPossivel()).isEqualTo(Precos.reais("23.00"));
        }

        @Test
        @DisplayName("grupo opcional pega todos os descontos e nenhum acréscimo")
        void opcional_pega_so_os_descontos() {
            Produto p = produtoDe("10.00");
            p.acrescentarGrupo(GrupoDeOpcoes.novo("Adicionais", 0, 3, 0, List.of(
                    Opcao.nova("Bacon", Precos.reais("6.00"), 0),
                    Opcao.nova("Sem queijo", Precos.reais("-2.00"), 1),
                    Opcao.nova("Sem cebola", Precos.reais("-3.00"), 2))));

            assertThat(p.precoMinimoPossivel())
                    .as("o consumidor escolhe os dois descontos e não escolhe o bacon")
                    .isEqualTo(Precos.reais("5.00"));
        }

        @Test
        @DisplayName("o teto limita quantos descontos cabem na mesma combinação")
        void o_teto_limita_os_descontos() {
            Produto p = produtoDe("10.00");
            p.acrescentarGrupo(GrupoDeOpcoes.novo("Adicionais", 0, 1, 0, List.of(
                    Opcao.nova("Sem queijo", Precos.reais("-2.00"), 0),
                    Opcao.nova("Sem cebola", Precos.reais("-3.00"), 1))));

            assertThat(p.precoMinimoPossivel())
                    .as("maxEscolhas 1: só o maior desconto entra")
                    .isEqualTo(Precos.reais("7.00"));
        }

        @Test
        @DisplayName("opção esgotada continua contando — C2 é do cadastro, não do dia")
        void a_opcao_esgotada_conta() {
            Produto p = produtoDe("10.00");
            GrupoDeOpcoes g = GrupoDeOpcoes.novo("Adicionais", 0, 1, 0, List.of(
                    Opcao.nova("Sem queijo", Precos.reais("-4.00"), 0)));
            p.acrescentarGrupo(g);
            p.marcarOpcao(g.id(), g.opcoes().getFirst().id(), Disponibilidade.esgotadoHoje(
                    Instant.parse("2026-09-25T22:00:00Z"), LocalDate.of(2026, 9, 25)));

            assertThat(p.precoMinimoPossivel())
                    .as("o desconto volta amanhã, e amanhã o produto sairia por 6,00")
                    .isEqualTo(Precos.reais("6.00"));
        }
    }

    @Nested
    class APublicacao {

        @Test
        @DisplayName("C2: combinação que zera o produto não publica")
        void c2_combinacao_que_zera() {
            Produto p = produtoDe("2.00");
            p.acrescentarGrupo(GrupoDeOpcoes.novo("Descontos", 0, 1, 0, List.of(
                    Opcao.nova("Sem queijo", Precos.reais("-2.00"), 0))));

            assertThatThrownBy(p::publicar)
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("C2");

            assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.RASCUNHO);
        }

        @Test
        @DisplayName("C2: combinação que deixa o preço negativo não publica")
        void c2_combinacao_negativa() {
            Produto p = produtoDe("2.00");
            p.acrescentarGrupo(GrupoDeOpcoes.novo("Descontos", 0, 1, 0, List.of(
                    Opcao.nova("Sem queijo", Precos.reais("-5.00"), 0))));

            assertThatThrownBy(p::publicar)
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("C2");
        }

        @Test
        @DisplayName("desconto que não chega a zerar publica — o cardápio com desconto é legítimo")
        void desconto_normal_publica() {
            Produto p = produtoDe("49.90");
            p.acrescentarGrupo(GrupoDeOpcoes.novo("Adicionais", 0, 2, 0, List.of(
                    Opcao.nova("Bacon", Precos.reais("6.00"), 0),
                    Opcao.nova("Sem queijo", Precos.reais("-2.00"), 1))));

            p.publicar();

            assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.ATIVO);
        }

        @Test
        @DisplayName("preço base zero continua sendo C1, e não C2 — a mensagem diz qual")
        void c1_vem_antes_de_c2() {
            assertThatThrownBy(() -> produtoDe("0.00").publicar())
                    .isInstanceOf(RegraDoCatalogoViolada.class)
                    .hasMessageContaining("C1");
        }
    }
}
