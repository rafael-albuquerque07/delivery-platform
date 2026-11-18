package com.deliveryplatform.merchant.application.port.in;

import com.deliveryplatform.merchant.domain.model.TipoDeOperacao;

import java.util.Objects;
import java.util.UUID;

/**
 * O que a {@code OperacaoDoEstabelecimentoPort} do {@code estabelecimento.md} §3 responde, do lado
 * do {@code merchant} (ADR-087).
 *
 * <p>{@code aberto} já compõe horário <b>e</b> pausa, pelo {@code Estabelecimento.estaAberta}: quem
 * pergunta não recompõe a regra das faixas que cruzam a meia-noite.
 *
 * <p>O {@code maxEntregasSimultaneas} que a porta declara não está aqui porque não existe no
 * domínio (ADR-087).
 */
public record OperacaoAtual(UUID estabelecimentoId, boolean aberto, TipoDeOperacao tipoDeOperacao) {

    public OperacaoAtual {
        Objects.requireNonNull(estabelecimentoId, "estabelecimentoId");
        Objects.requireNonNull(tipoDeOperacao, "tipoDeOperacao");
    }
}
