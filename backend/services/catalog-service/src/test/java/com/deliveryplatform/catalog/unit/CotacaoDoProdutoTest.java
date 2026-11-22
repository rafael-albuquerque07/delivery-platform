package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.exception.OpcoesIndisponiveis;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada.Motivo;
import com.deliveryplatform.catalog.domain.model.Disponibilidade;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.LinhaCotada;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.OpcaoCotada;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code Produto.cotar}: a aritmética e a ordem das checagens, sem Spring (ADR-088). */
class CotacaoDoProdutoTest {

    private static final Disponibilidade ESGOTADA = Disponibilidade.esgotadoHoje(
            Instant.parse("2026-09-25T22:00:00Z"), LocalDate.of(2026, 9, 25));

    private final Opcao pequena = Opcao.nova("Pequena", Precos.reais("0.00"), 0);
    private final Opcao grande = Opcao.nova("Grande", Precos.reais("15.00"), 1);
    private final Opcao catupiry = Opcao.nova("Catupiry", Precos.reais("8.00"), 0);
    private final Opcao cheddar = Opcao.nova("Cheddar", Precos.reais("6.00"), 1);

    private Produto pizza(Opcao... trocas) {
        List<Opcao> tamanho = substituir(List.of(pequena, grande), trocas);
        List<Opcao> borda = substituir(List.of(catupiry, cheddar), trocas);
        Produto p = Produto.rascunho(ProdutoDeTeste.LOJA, ProdutoDeTeste.CATEGORIA,
                "Pizza de calabresa", Precos.reais("40.00"), ModoDeControle.QUALITATIVO, 0);
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Borda", 0, 1, 1, borda));
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Tamanho", 1, 1, 0, tamanho));
        p.publicar();
        return p;
    }

    private static List<Opcao> substituir(List<Opcao> opcoes, Opcao... trocas) {
        return opcoes.stream().map(o -> {
            for (Opcao t : trocas) {
                if (t.id().equals(o.id())) {
                    return t;
                }
            }
            return o;
        }).toList();
    }

    @Test
    @DisplayName("preço unitário é a base mais os acréscimos, e o subtotal multiplica pela quantidade")
    void a_aritmetica() {
        LinhaCotada linha = pizza().cotar(2, List.of(catupiry.id(), grande.id()));

        assertThat(linha.precoUnitario().valor()).isEqualByComparingTo("63.00");
        assertThat(linha.subtotal().valor()).isEqualByComparingTo("126.00");
        assertThat(linha.quantidade()).isEqualTo(2);
        assertThat(linha.opcoes()).extracting(OpcaoCotada::nomeDoGrupo).containsExactly("Tamanho", "Borda");
    }

    @Test
    @DisplayName("sem opção escolhida num grupo opcional, a linha sai com a base")
    void grupo_opcional_vazio() {
        LinhaCotada linha = pizza().cotar(1, List.of(pequena.id()));

        assertThat(linha.precoUnitario().valor()).isEqualByComparingTo("40.00");
        assertThat(linha.opcoes()).extracting(OpcaoCotada::nomeDaOpcao).containsExactly("Pequena");
    }

    @Test
    @DisplayName("quantidade menor que 1 é QUANTIDADE_INVALIDA")
    void quantidade_invalida() {
        assertMotivo(() -> pizza().cotar(0, List.of(pequena.id())), Motivo.QUANTIDADE_INVALIDA);
    }

    @Test
    @DisplayName("opção que não é do produto vence a contagem do grupo: OPCAO_FORA_DO_PRODUTO")
    void fora_do_produto_vem_antes_dos_limites() {
        assertMotivo(() -> pizza().cotar(1, List.of(UUID.randomUUID())), Motivo.OPCAO_FORA_DO_PRODUTO);
    }

    @Test
    @DisplayName("a mesma opção duas vezes é OPCAO_REPETIDA, antes do máximo do grupo")
    void repetida_vem_antes_do_maximo() {
        assertMotivo(() -> pizza().cotar(1, List.of(grande.id(), grande.id())), Motivo.OPCAO_REPETIDA);
    }

    @Test
    @DisplayName("abaixo do mínimo e acima do máximo: ESCOLHAS_FORA_DOS_LIMITES, com o grupo, os limites e o que veio")
    void fora_dos_limites() {
        assertThatThrownBy(() -> pizza().cotar(1, List.of(catupiry.id())))
                .isInstanceOfSatisfying(RegraDoCatalogoViolada.class, e -> {
                    assertThat(e.motivo()).isEqualTo(Motivo.ESCOLHAS_FORA_DOS_LIMITES);
                    assertThat(e.dados()).containsExactly("Tamanho", "1", "1", "0");
                });
        assertMotivo(() -> pizza().cotar(1, List.of(pequena.id(), catupiry.id(), cheddar.id())),
                Motivo.ESCOLHAS_FORA_DOS_LIMITES);
    }

    @Test
    @DisplayName("a estrutura vem antes da disponibilidade: opção esgotada num grupo abaixo do mínimo é 400")
    void estrutura_antes_da_disponibilidade() {
        Produto p = pizza(catupiry.com(ESGOTADA));

        assertMotivo(() -> p.cotar(1, List.of(catupiry.id())), Motivo.ESCOLHAS_FORA_DOS_LIMITES);
    }

    @Test
    @DisplayName("todas as opções escolhidas indisponíveis, na ordem dos grupos — não só a primeira")
    void todas_as_indisponiveis() {
        Produto p = pizza(grande.com(ESGOTADA), catupiry.com(ESGOTADA));

        assertThatThrownBy(() -> p.cotar(1, List.of(catupiry.id(), grande.id())))
                .isInstanceOfSatisfying(OpcoesIndisponiveis.class, e ->
                        assertThat(e.opcoes()).containsExactly(grande.id(), catupiry.id()));
    }

    private static void assertMotivo(org.assertj.core.api.ThrowableAssert.ThrowingCallable chamada,
                                     Motivo motivo) {
        assertThatThrownBy(chamada).isInstanceOfSatisfying(RegraDoCatalogoViolada.class,
                e -> assertThat(e.motivo()).isEqualTo(motivo));
    }
}
