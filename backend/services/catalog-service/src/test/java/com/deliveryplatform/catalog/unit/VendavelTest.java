package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.model.Disponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A fórmula da §5, cláusula por cláusula.
 *
 * <pre>
 * vendavel = ATIVO ∧ disponibilidade ∈ {DISPONIVEL, ACABANDO}
 *          ∧ ∀ grupo obrigatório: opções disponíveis ≥ minEscolhas
 * </pre>
 *
 * <p>A terceira cláusula é a que este arquivo existe para proteger. Ela é a
 * única que não se lê no próprio produto: depende de quantas opções de um
 * grupo estão de pé <i>hoje</i>.
 */
class VendavelTest {

    private static final Instant AGORA = Instant.parse("2026-09-26T22:00:00Z");
    private static final LocalDate EXPEDIENTE = LocalDate.of(2026, 9, 26);

    // ── primeira cláusula: publicação ───────────────────────────────────────

    @Test
    void c10_rascunho_nao_e_vendavel() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(3));

        assertThat(p.vendavel()).isFalse();
    }

    @Test
    void c10_inativo_nao_e_vendavel() {
        Produto p = ProdutoDeTeste.margheritaPublicada();
        p.inativar();

        assertThat(p.vendavel()).isFalse();
    }

    // ── segunda cláusula: disponibilidade ───────────────────────────────────

    @ParameterizedTest(name = "{0} → vendável {1}")
    @CsvSource({
            "DISPONIVEL,    true",
            "ACABANDO,      true",
            "ESGOTADO_HOJE, false",
            "ESGOTADO_INDETERMINADO, false"
    })
    @DisplayName("ACABANDO vende: é aviso ao consumidor, não é trava")
    void os_quatro_estados(EstadoDeDisponibilidade estado, boolean esperado) {
        Produto p = ProdutoDeTeste.margheritaPublicada();
        p.marcar(new Disponibilidade(estado, AGORA, EXPEDIENTE));

        assertThat(p.vendavel()).isEqualTo(esperado);
    }

    // ── terceira cláusula: os grupos ────────────────────────────────────────

    @Test
    @DisplayName("a pizza DISPONIVEL cujo Tamanho inteiro esgotou NÃO é vendável")
    void grupo_obrigatorio_sem_opcao_disponivel() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(0));
        p.publicar();

        assertThat(p.getEstadoDePublicacao().name()).isEqualTo("ATIVO");
        assertThat(p.getDisponibilidade().estado())
                .isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);

        assertThat(p.vendavel())
                .as("não existe pedido válido a montar: o consumidor é obrigado a escolher "
                        + "um tamanho e não há tamanho. Sem esta cláusula ele descobre isso "
                        + "no carrinho, e a cotação recusa o que o cardápio ofereceu")
                .isFalse();
    }

    @Test
    @DisplayName("uma opção de pé já basta para um grupo de escolha única")
    void uma_opcao_basta() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(1));
        p.publicar();

        assertThat(p.vendavel()).isTrue();
    }

    @Test
    @DisplayName("grupo opcional inteiro esgotado não impede venda — não se escolhe adicional")
    void grupo_opcional_esgotado_nao_impede() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(3));
        p.acrescentarGrupo(ProdutoDeTeste.adicionais(false));
        p.publicar();

        assertThat(p.vendavel()).isTrue();
    }

    @Test
    @DisplayName("basta um grupo obrigatório de pé faltar para o produto cair")
    void um_grupo_derruba_o_produto() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(0));
        p.acrescentarGrupo(ProdutoDeTeste.adicionais(true));
        p.publicar();

        assertThat(p.vendavel()).isFalse();
    }

    @Test
    void produto_publicado_sem_nenhum_grupo_e_vendavel() {
        Produto p = ProdutoDeTeste.margherita();
        p.publicar();

        assertThat(p.vendavel()).isTrue();
    }

    // ── o que o vendável não é ──────────────────────────────────────────────

    @Test
    @DisplayName("o vendável é derivado: não há campo, não há setter, não há o que sair de sincronia")
    void c6_nao_existe_setter_de_vendavel() {
        assertThat(Produto.class.getDeclaredFields())
                .as("um booleano armazenado teria cinco caminhos de escrita — publicação, "
                        + "inativação, marcação, opção que esgota, abertura de expediente — "
                        + "e o quinto que alguém esquecesse mentiria para sempre")
                .noneMatch(f -> f.getName().toLowerCase().contains("vendavel"));

        assertThat(Produto.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().startsWith("setVendavel"));
    }
}
