package com.deliveryplatform.merchant.api.dto;

import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Permissao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Nem nome nem telefone, e não é esquecimento.</b> Quem é a pessoa por trás
 * do {@code usuarioId} é dado do {@code identity-service}, e a ADR-001 proíbe
 * este serviço de importá-lo. A porta síncrona entre os dois não existe — nasce
 * na rodada que precisar dela.
 *
 * <p>Então a tela de equipe, hoje, mostra identificadores. Isso é desconfortável
 * e é honesto: <b>a alternativa seria o {@code merchant} guardar uma cópia do
 * nome</b>, que envelheceria na primeira vez que alguém se casasse, e que
 * espalharia dado pessoal por um segundo banco contra a ADR-013. O desconforto é
 * a fronteira aparecendo onde ela está.
 */
public record MembroResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID usuarioId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Papel papel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EstadoDoMembro estado,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<Permissao> permissoes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant desde) {
}
