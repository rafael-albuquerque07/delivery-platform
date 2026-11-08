package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tirar do ar (ADR-078), pela raiz: o que a transição faz, o que ela não toca, e o
 * caminho de volta que ela abre para a composição.
 */
class DespublicacaoDoProdutoTest {

    @Test
    @DisplayName("tirar do ar não mexe na disponibilidade: o que o dia fez fica como estava")
    void disponibilidade_fica() {
        Produto p = ProdutoDeTeste.esgotadoHojeEm(LocalDate.of(2026, 11, 7));

        p.inativar();

        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.INATIVO);
        assertThat(p.getDisponibilidade().estado()).isEqualTo(EstadoDeDisponibilidade.ESGOTADO_HOJE);
        assertThat(p.getDisponibilidade().expedienteDeReferencia()).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    @DisplayName("tirar do ar o que já saiu não faz nada e não estoura")
    void inativar_e_idempotente() {
        Produto p = ProdutoDeTeste.margheritaPublicada();
        p.inativar();

        p.inativar();

        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.INATIVO);
    }

    /**
     * O beco da E-6, pela raiz: no {@code ATIVO} o grupo vazio é recusado; fora do ar ele
     * entra, a publicação o recusa enquanto estiver vazio, e aceita depois da opção.
     */
    @Test
    @DisplayName("fora do ar o grupo entra vazio, e a volta ao cardápio cobra a C4 até a opção chegar")
    void o_beco_fechado() {
        Produto p = ProdutoDeTeste.margheritaPublicada();
        assertThatThrownBy(() -> p.criarGrupo("Bebida", 0, 1, 5))
                .isInstanceOf(RegraDoCatalogoViolada.class);

        p.inativar();
        GrupoDeOpcoes bebida = p.criarGrupo("Bebida", 0, 1, 5);

        assertThatThrownBy(p::publicar)
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .extracting(e -> ((RegraDoCatalogoViolada) e).motivo())
                .isEqualTo(RegraDoCatalogoViolada.Motivo.PUBLICACAO_TETO_INALCANCAVEL);
        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.INATIVO);

        p.criarOpcao(bebida.id(), "Guaraná", Precos.reais("7.00"), 0);
        p.publicar();

        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.ATIVO);
        assertThat(p.getGruposDeOpcoes()).extracting(GrupoDeOpcoes::nome)
                .containsExactly("Tamanho", "Bebida");
    }
}
