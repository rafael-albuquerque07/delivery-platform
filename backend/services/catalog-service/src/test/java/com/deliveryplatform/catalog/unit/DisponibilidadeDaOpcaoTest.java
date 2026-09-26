package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.domain.model.Disponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.Precos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.deliveryplatform.catalog.support.ProdutoDeTeste.CATEGORIA;
import static com.deliveryplatform.catalog.support.ProdutoDeTeste.LOJA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A opção com os quatro estados do {@code catalogo.md} §3 — o caso que mais
 * importa, porque <b>o que acaba é o sabor, não o produto</b>.
 *
 * <p>A calabresa acaba às 23h. "Pizza grande" não acaba. Se a opção não tiver
 * {@code expedienteDeReferencia}, ela não volta na abertura seguinte, e o
 * comerciante tem de religar cada sabor toda manhã.
 */
class DisponibilidadeDaOpcaoTest {

    private static final Instant SEXTA_AS_19H = Instant.parse("2026-09-25T22:00:00Z");
    private static final LocalDate SEXTA = LocalDate.of(2026, 9, 25);
    private static final LocalDate SABADO = LocalDate.of(2026, 9, 26);

    private GrupoDeOpcoes sabores;

    private Produto pizzaComDoisSabores() {
        Produto p = Produto.rascunho(LOJA, CATEGORIA, "Pizza grande", Precos.reais("49.90"),
                ModoDeControle.QUALITATIVO, 0);
        sabores = GrupoDeOpcoes.novo("Sabores", 1, 1, 0, List.of(
                Opcao.nova("Calabresa", Precos.reais("0.00"), 0),
                Opcao.nova("Marguerita", Precos.reais("0.00"), 1)));
        p.acrescentarGrupo(sabores);
        p.publicar();
        return p;
    }

    private static Opcao primeira(Produto p) {
        return p.getGruposDeOpcoes().getFirst().opcoes().getFirst();
    }

    @Test
    @DisplayName("a opção nasce disponível e sem carimbo")
    void nasce_disponivel() {
        Opcao calabresa = primeira(pizzaComDoisSabores());

        assertThat(calabresa.disponivel()).isTrue();
        assertThat(calabresa.disponibilidade().estado())
                .isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);
        assertThat(calabresa.disponibilidade().marcadoEm()).isNull();
    }

    @Test
    @DisplayName("um sabor esgotado não derruba o produto — ainda há o que escolher")
    void um_sabor_esgotado_nao_derruba() {
        Produto p = pizzaComDoisSabores();

        p.marcarOpcao(sabores.id(), sabores.opcoes().getFirst().id(),
                Disponibilidade.esgotadoHoje(SEXTA_AS_19H, SEXTA));

        assertThat(primeira(p).disponivel()).isFalse();
        assertThat(p.getGruposDeOpcoes().getFirst().satisfazivelHoje()).isTrue();
        assertThat(p.vendavel()).isTrue();
    }

    @Test
    @DisplayName("os dois sabores esgotados derrubam o produto, e a publicação fica intacta")
    void os_dois_esgotados_derrubam() {
        Produto p = pizzaComDoisSabores();

        for (Opcao o : sabores.opcoes()) {
            p.marcarOpcao(sabores.id(), o.id(), Disponibilidade.esgotadoHoje(SEXTA_AS_19H, SEXTA));
        }

        assertThat(p.vendavel()).isFalse();
        assertThat(p.getEstadoDePublicacao())
                .as("esgotar é do dia; publicar é do cadastro. O prato não vai para "
                        + "rascunho porque acabou o recheio")
                .isEqualTo(EstadoDePublicacao.ATIVO);
    }

    @Test
    @DisplayName("C11: a opção esgotada ontem reativa hoje, pela mesma pergunta do produto")
    void c11_a_opcao_reativa_no_expediente_seguinte() {
        Produto p = pizzaComDoisSabores();
        p.marcarOpcao(sabores.id(), sabores.opcoes().getFirst().id(),
                Disponibilidade.esgotadoHoje(SEXTA_AS_19H, SEXTA));

        Disponibilidade d = primeira(p).disponibilidade();

        assertThat(d.deveReativarNoExpediente(SABADO)).isTrue();
        assertThat(d.deveReativarNoExpediente(SEXTA))
                .as("a padaria que abre duas vezes no mesmo dia não ressuscita o pão")
                .isFalse();
    }

    @Test
    @DisplayName("marcar preserva id, nome, acréscimo, ordem e o grupo inteiro")
    void marcar_preserva_o_resto() {
        Produto p = pizzaComDoisSabores();
        Opcao antes = sabores.opcoes().getFirst();

        p.marcarOpcao(sabores.id(), antes.id(),
                Disponibilidade.esgotadoHoje(SEXTA_AS_19H, SEXTA));

        Opcao depois = primeira(p);
        assertThat(depois.id()).isEqualTo(antes.id());
        assertThat(depois.nome()).isEqualTo("Calabresa");
        assertThat(depois.acrescimo()).isEqualTo(antes.acrescimo());
        assertThat(depois.ordem()).isEqualTo(antes.ordem());

        GrupoDeOpcoes grupo = p.getGruposDeOpcoes().getFirst();
        assertThat(grupo.id()).isEqualTo(sabores.id());
        assertThat(grupo.nome()).isEqualTo("Sabores");
        assertThat(grupo.minEscolhas()).isEqualTo(1);
        assertThat(grupo.opcoes()).hasSize(2);
    }

    @Test
    @DisplayName("id que não existe no produto estoura — silêncio aqui é opção que nunca volta")
    void id_desconhecido_estoura() {
        Produto p = pizzaComDoisSabores();
        UUID opcao = sabores.opcoes().getFirst().id();

        assertThatThrownBy(() -> p.marcarOpcao(UUID.randomUUID(), opcao, Disponibilidade.inicial()))
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .hasMessageContaining("não encontrada");

        assertThatThrownBy(() ->
                p.marcarOpcao(sabores.id(), UUID.randomUUID(), Disponibilidade.inicial()))
                .as("a reativação vai varrer opções por id; engolir o id errado faria "
                        + "um sabor ficar esgotado para sempre, sem erro em lugar nenhum")
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .hasMessageContaining("não encontrada");
    }

    @Test
    @DisplayName("marcar o mesmo estado duas vezes é inofensivo — a entrega é pelo menos uma vez")
    void marcar_o_mesmo_estado_duas_vezes() {
        Produto p = pizzaComDoisSabores();
        UUID opcao = sabores.opcoes().getFirst().id();
        Disponibilidade esgotada = Disponibilidade.esgotadoHoje(SEXTA_AS_19H, SEXTA);

        p.marcarOpcao(sabores.id(), opcao, esgotada);
        p.marcarOpcao(sabores.id(), opcao, esgotada);

        assertThat(primeira(p).disponivel())
                .as("o consumidor do ExpedienteAlteradoV1 recebe o mesmo evento duas vezes "
                        + "(ADR-043 §3). Se a segunda passada estourasse, a idempotência "
                        + "de C11 quebraria dentro do agregado, e não no consumidor")
                .isFalse();
    }

    @Test
    @DisplayName("a opção não nasce sem disponibilidade")
    void sem_disponibilidade() {
        assertThatThrownBy(() ->
                new Opcao(UUID.randomUUID(), "Calabresa", Precos.reais("0.00"), null, 0))
                .isInstanceOf(RegraDoCatalogoViolada.class)
                .hasMessageContaining("sem disponibilidade");
    }
}
