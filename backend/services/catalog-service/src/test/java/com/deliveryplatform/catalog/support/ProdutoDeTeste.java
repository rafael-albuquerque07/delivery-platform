package com.deliveryplatform.catalog.support;

import com.deliveryplatform.catalog.domain.model.Disponibilidade;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A pizzaria do {@code catalogo.md}, montada em uma linha.
 *
 * <p>Mesmo papel do {@code LojaDeTeste} no {@code merchant}: o teste que
 * precisa montar sete objetos para afirmar uma coisa acaba afirmando o
 * montador. Aqui os casos dizem só o que os distingue.
 */
public final class ProdutoDeTeste {

    public static final UUID LOJA = UUID.randomUUID();
    public static final UUID CATEGORIA = UUID.randomUUID();

    private static final Instant ONTEM_A_NOITE = Instant.parse("2026-09-25T22:00:00Z");
    private static final LocalDate EXPEDIENTE_DE_ONTEM = LocalDate.of(2026, 9, 25);

    private ProdutoDeTeste() {
    }

    /**
     * Esgotar num teste passou a exigir carimbo — não existe mais "false".
     *
     * <p>É o custo da rodada, e é o custo certo: um teste que esgota uma opção
     * agora é obrigado a dizer <i>de que expediente</i>, que é a informação sem
     * a qual ela nunca voltaria.
     */
    public static Disponibilidade estado(boolean disponivel) {
        return disponivel
                ? Disponibilidade.inicial()
                : Disponibilidade.esgotadoHoje(ONTEM_A_NOITE, EXPEDIENTE_DE_ONTEM);
    }

    /** Margherita, R$ 49,90, controle qualitativo, sem nenhum grupo. */
    public static Produto margherita() {
        return Produto.rascunho(LOJA, CATEGORIA, "Pizza margherita",
                Precos.reais("49.90"), ModoDeControle.QUALITATIVO, 0);
    }

    /** Refrigerante em lata: não acaba. */
    public static Produto refrigerante() {
        return Produto.rascunho(LOJA, CATEGORIA, "Refrigerante lata",
                Precos.reais("7.00"), ModoDeControle.SEM_CONTROLE, 1);
    }

    /**
     * "Tamanho": escolha exatamente um, três opções.
     *
     * @param disponiveis quantas das três estão disponíveis, da primeira para a
     *                    última — é o eixo que os testes do vendável variam
     */
    public static GrupoDeOpcoes tamanho(int disponiveis) {
        List<Opcao> opcoes = List.of(
                Opcao.nova("Pequena", Precos.reais("0.00"), 0).com(estado(disponiveis > 0)),
                Opcao.nova("Média", Precos.reais("8.00"), 1).com(estado(disponiveis > 1)),
                Opcao.nova("Grande", Precos.reais("16.00"), 2).com(estado(disponiveis > 2)));
        return GrupoDeOpcoes.novo("Tamanho", 1, 1, 0, opcoes);
    }

    /** "Adicionais": opcional, até dois — as duas que existem (C4) —, todos disponíveis ou todos não. */
    public static GrupoDeOpcoes adicionais(boolean disponiveis) {
        List<Opcao> opcoes = List.of(
                Opcao.nova("Bacon", Precos.reais("6.00"), 0).com(estado(disponiveis)),
                Opcao.nova("Sem cebola", Precos.reais("-2.00"), 1).com(estado(disponiveis)));
        return GrupoDeOpcoes.novo("Adicionais", 0, 2, 1, opcoes);
    }

    /** Grupo obrigatório que ninguém consegue satisfazer: zero opções. */
    public static GrupoDeOpcoes bordaSemNenhumaBorda() {
        return GrupoDeOpcoes.novo("Borda", 1, 1, 2, List.of());
    }

    /** Margherita publicada, com "Tamanho" cheio — o caso normal. */
    public static Produto margheritaPublicada() {
        Produto p = margherita();
        p.acrescentarGrupo(tamanho(3));
        p.publicar();
        return p;
    }
}
