package com.deliveryplatform.merchant.domain.model;

/**
 * Dois valores, e uma função só (`estabelecimento.md` §2).
 *
 * <p><b>Papel não é uma lista de permissões.</b> Toda autorização real é feita
 * item a item, pelo conjunto de {@link Permissao} do vínculo. O papel existe
 * para impedir escalada: {@code ADMINISTRADOR} é quem
 * {@link Permissao#GERENCIAR_EQUIPE} sozinha não alcança (M3).
 *
 * <p>Presets de tela — "Atendente", "Gerente" — expandem para um conjunto de
 * permissões no momento do convite e <b>não são guardados</b>. Preset guardado
 * vira papel de fato, e aí a matriz item a item que o PRD pediu deixa de
 * existir na prática.
 */
public enum Papel {

    ADMINISTRADOR,
    COLABORADOR
}
