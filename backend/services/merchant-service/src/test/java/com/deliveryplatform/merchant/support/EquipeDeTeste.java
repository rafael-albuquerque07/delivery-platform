package com.deliveryplatform.merchant.support;

import com.deliveryplatform.merchant.domain.model.Equipe;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Permissao;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A equipe da pizzaria da Marli, para os testes de unidade e de integração.
 *
 * <p>São as três pessoas que o PRD descreve: a <b>Marli</b>, dona, que cadastrou
 * a loja e por isso é a fundadora; o <b>Júnior</b>, que cuida do salão e do
 * time, com {@code GERENCIAR_EQUIPE}; e a <b>Bia</b>, atendente, que só vê e
 * mexe em pedido. Uma falha nomeia a pessoa, e não o membro número dois.
 */
public final class EquipeDeTeste {

    public static final Instant AGORA = Instant.parse("2026-09-14T12:00:00Z");

    /** As de quem cuida do time sem ser dono: gerencia equipe e vê as vendas. */
    public static final Set<Permissao> DO_JUNIOR = EnumSet.of(
            Permissao.GERENCIAR_EQUIPE,
            Permissao.VER_VENDAS,
            Permissao.VER_PEDIDO,
            Permissao.ALTERAR_STATUS);

    /** As de quem atende: vê e mexe em pedido, e nada além disso. */
    public static final Set<Permissao> DA_BIA = EnumSet.of(
            Permissao.VER_PEDIDO,
            Permissao.ALTERAR_STATUS,
            Permissao.VER_PRODUTO);

    private EquipeDeTeste() {
    }

    public static Membro marli(UUID loja) {
        return Membro.fundador(UUID.randomUUID(), loja, AGORA);
    }

    public static Membro junior(UUID loja) {
        return Membro.colaborador(UUID.randomUUID(), loja, DO_JUNIOR, AGORA);
    }

    public static Membro bia(UUID loja) {
        return Membro.colaborador(UUID.randomUUID(), loja, DA_BIA, AGORA);
    }

    public static Equipe pizzaria(UUID loja, Membro... membros) {
        return Equipe.de(loja, List.of(membros));
    }
}
