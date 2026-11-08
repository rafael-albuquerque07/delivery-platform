package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.exception.GrupoNaoEncontrado;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O comerciante monta as perguntas do produto pela raiz (ADR-077): {@code criarGrupo}
 * e {@code criarOpcao}. Em rascunho, só a C3; no produto {@code ATIVO}, as invariantes
 * da publicação são recobradas — e a recusa deixa a lista como estava.
 */
class MontagemDeOpcoesTest {

    @Test
    @DisplayName("em rascunho, o grupo nasce vazio e entra na ordem — a C4 espera a publicação")
    void grupo_em_rascunho() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(ProdutoDeTeste.adicionais(true)); // ordem 1

        GrupoDeOpcoes criado = p.criarGrupo("Tamanho", 1, 1, 0);

        assertThat(criado.opcoes()).isEmpty();
        assertThat(p.getGruposDeOpcoes()).extracting(GrupoDeOpcoes::nome)
                .containsExactly("Tamanho", "Adicionais");
        assertThat(p.getGruposDeOpcoes().get(0).id()).isEqualTo(criado.id());
        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.RASCUNHO);
    }

    @Test
    @DisplayName("C3 vale em qualquer estado: mínimo maior que o máximo é recusado, e nada entra")
    void c3_recusa() {
        Produto p = ProdutoDeTeste.margherita();

        assertThatThrownBy(() -> p.criarGrupo("Tamanho", 2, 1, 0))
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .extracting(e -> ((RegraDoCatalogoViolada) e).motivo())
                .isEqualTo(RegraDoCatalogoViolada.Motivo.LIMITES_DO_GRUPO);
        assertThat(p.getGruposDeOpcoes()).isEmpty();
    }

    @Test
    @DisplayName("a opção entra no grupo que já existe, na ordem dela, disponível")
    void opcao_em_grupo_existente() {
        Produto p = ProdutoDeTeste.margherita();
        GrupoDeOpcoes grupo = p.criarGrupo("Tamanho", 1, 1, 0);

        p.criarOpcao(grupo.id(), "Grande", Precos.reais("16.00"), 2);
        Opcao pequena = p.criarOpcao(grupo.id(), "Pequena", Precos.zero(), 0);

        GrupoDeOpcoes depois = p.getGruposDeOpcoes().get(0);
        assertThat(depois.id()).isEqualTo(grupo.id());
        assertThat(depois.opcoes()).extracting(Opcao::nome).containsExactly("Pequena", "Grande");
        assertThat(depois.opcoes().get(0).id()).isEqualTo(pequena.id());
        assertThat(depois.opcoes()).allMatch(Opcao::disponivel);
    }

    @Test
    @DisplayName("em rascunho, o acréscimo que tornaria o produto de graça entra — a C2 espera a publicação")
    void rascunho_nao_recobra() {
        Produto p = ProdutoDeTeste.margherita();
        GrupoDeOpcoes grupo = p.criarGrupo("Tamanho", 1, 1, 0);

        p.criarOpcao(grupo.id(), "Cortesia", Precos.reais("-60.00"), 0);

        assertThat(p.getGruposDeOpcoes().get(0).opcoes()).hasSize(1);
    }

    @Test
    @DisplayName("grupo que não é deste produto: GrupoNaoEncontrado, e nada muda")
    void grupo_desconhecido() {
        Produto p = ProdutoDeTeste.margherita();
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(3));
        List<GrupoDeOpcoes> antes = p.getGruposDeOpcoes();

        assertThatThrownBy(() -> p.criarOpcao(UUID.randomUUID(), "Grande", Precos.zero(), 0))
                .isInstanceOf(GrupoNaoEncontrado.class);
        assertThat(p.getGruposDeOpcoes()).isEqualTo(antes);
    }

    @Test
    @DisplayName("ATIVO: grupo vazio cai na C4 recobrada, e a lista volta a ser a de antes")
    void ativo_grupo_vazio_recusado() {
        Produto p = ProdutoDeTeste.margheritaPublicada();
        List<GrupoDeOpcoes> antes = p.getGruposDeOpcoes();

        assertThatThrownBy(() -> p.criarGrupo("Bebida", 0, 1, 5))
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .extracting(e -> ((RegraDoCatalogoViolada) e).motivo())
                .isEqualTo(RegraDoCatalogoViolada.Motivo.PUBLICACAO_TETO_INALCANCAVEL);
        assertThat(p.getGruposDeOpcoes()).isEqualTo(antes);
        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.ATIVO);
    }

    @Test
    @DisplayName("ATIVO: opção que faz existir combinação de graça cai na C2, e o grupo fica como estava")
    void ativo_opcao_c2_recusada() {
        Produto p = ProdutoDeTeste.margheritaPublicada();
        GrupoDeOpcoes tamanho = p.getGruposDeOpcoes().get(0);
        List<GrupoDeOpcoes> antes = p.getGruposDeOpcoes();

        assertThatThrownBy(() -> p.criarOpcao(tamanho.id(), "Cortesia", Precos.reais("-60.00"), 3))
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .extracting(e -> ((RegraDoCatalogoViolada) e).motivo())
                .isEqualTo(RegraDoCatalogoViolada.Motivo.PUBLICACAO_PRECO_NAO_POSITIVO);
        assertThat(p.getGruposDeOpcoes()).isEqualTo(antes);
    }

    @Test
    @DisplayName("ATIVO: opção que mantém as invariantes entra, e o produto segue no cardápio")
    void ativo_opcao_valida_entra() {
        Produto p = ProdutoDeTeste.margheritaPublicada();
        GrupoDeOpcoes tamanho = p.getGruposDeOpcoes().get(0);

        p.criarOpcao(tamanho.id(), "Família", Precos.reais("24.00"), 3);

        assertThat(p.getGruposDeOpcoes().get(0).opcoes()).extracting(Opcao::nome)
                .containsExactly("Pequena", "Média", "Grande", "Família");
        assertThat(p.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.ATIVO);
        assertThat(p.vendavel()).isTrue();
    }
}
