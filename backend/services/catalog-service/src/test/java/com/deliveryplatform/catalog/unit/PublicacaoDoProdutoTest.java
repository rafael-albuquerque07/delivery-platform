package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As transições de publicação — e a distinção que a rodada inteira protege:
 * <b>o que nunca pode ser satisfeito trava a publicação; o que acabou hoje não
 * trava nada.</b>
 */
class PublicacaoDoProdutoTest {

    @Test
    void rascunho_vai_a_ativo() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(3));

        p.publicar();

        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.ATIVO);
    }

    @Test
    @DisplayName("produto sem nenhum grupo publica — nem todo prato tem escolha")
    void sem_grupo_publica() {
        Produto p = ProdutoDeTeste.margherita();

        p.publicar();

        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.ATIVO);
    }

    @Test
    @DisplayName("grupo obrigatório sem nenhuma opção impede a publicação — é defeito de cadastro")
    void c5_grupo_obrigatorio_vazio_trava() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(ProdutoDeTeste.bordaSemNenhumaBorda());

        assertThatThrownBy(p::publicar)
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .hasMessageContaining("C5")
                .hasMessageContaining("Borda");

        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.RASCUNHO);
    }

    @Test
    @DisplayName("grupo obrigatório com TODAS as opções esgotadas publica — isso é hoje, não é cadastro")
    void grupo_obrigatorio_todo_esgotado_publica() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(0));

        p.publicar();

        assertThat(p.getEstadoDePublicacao())
                .as("se esgotar travasse a publicação, o cardápio se despublicaria sozinho "
                        + "numa sexta à noite e o comerciante encontraria o prato em rascunho "
                        + "na segunda, sem saber quem mexeu")
                .isEqualTo(EstadoDePublicacao.ATIVO);
        assertThat(p.vendavel())
                .as("publicado e não vendável é exatamente o estado certo aqui")
                .isFalse();
    }

    @Test
    @DisplayName("publicar o que já está publicado não faz nada e não estoura")
    void publicar_e_idempotente() {
        Produto p = ProdutoDeTeste.margheritaPublicada();

        p.publicar();

        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.ATIVO);
    }

    @Test
    void ativo_vai_a_inativo_e_volta() {
        Produto p = ProdutoDeTeste.margheritaPublicada();

        p.inativar();
        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.INATIVO);

        p.publicar();
        assertThat(p.getEstadoDePublicacao())
                .as("o sanduíche de inverno volta em maio sem ser remontado")
                .isEqualTo(EstadoDePublicacao.ATIVO);
    }

    @Test
    @DisplayName("rascunho não se inativa: ele nunca apareceu no cardápio")
    void rascunho_nao_inativa() {
        Produto p = ProdutoDeTeste.margherita();

        assertThatThrownBy(p::inativar)
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .hasMessageContaining("rascunho");

        assertThat(p.getEstadoDePublicacao())
                .as("se essa transição passasse, RASCUNHO e INATIVO significariam a mesma "
                        + "coisa e a lista do que está sendo montado sumiria dentro da lista "
                        + "do que foi tirado do ar")
                .isEqualTo(EstadoDePublicacao.RASCUNHO);
    }

    @Test
    @DisplayName("C1: preço base zero não publica — produto de graça por descuido")
    void c1_preco_zero_impede_a_publicacao() {
        Produto p = Produto.rascunho(ProdutoDeTeste.LOJA, ProdutoDeTeste.CATEGORIA,
                "Pizza do dia", Precos.zero(), ModoDeControle.QUALITATIVO, 0);

        assertThatThrownBy(p::publicar)
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .hasMessageContaining("C1");

        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.RASCUNHO);
    }

    @Test
    @DisplayName("C4: teto maior que o número de opções não publica — promete escolha que não existe")
    void c4_teto_inalcancavel_impede_a_publicacao() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(GrupoDeOpcoes.novo("Adicionais", 0, 3, 1, List.of(
                Opcao.nova("Bacon", Precos.reais("6.00"), 0))));

        assertThatThrownBy(p::publicar)
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .hasMessageContaining("C4")
                .hasMessageContaining("Adicionais");

        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.RASCUNHO);
    }

    @Test
    @DisplayName("a volta de INATIVO também é cobrada: grupo impossível continua travando")
    void volta_de_inativo_revalida() {
        Produto p = ProdutoDeTeste.margheritaPublicada();
        p.inativar();
        p.acrescentarGrupo(ProdutoDeTeste.bordaSemNenhumaBorda());

        assertThatThrownBy(p::publicar).isInstanceOf(RegraDoCatalogoViolada.class);

        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.INATIVO);
    }
}
