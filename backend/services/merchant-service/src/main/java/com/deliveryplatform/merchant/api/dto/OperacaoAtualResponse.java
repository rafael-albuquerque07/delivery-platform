package com.deliveryplatform.merchant.api.dto;

import com.deliveryplatform.merchant.application.port.in.OperacaoAtual;
import com.deliveryplatform.merchant.domain.model.TipoDeOperacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** O que a operação da loja responde, e nada mais (ADR-087). */
@Schema(description = "Se a loja está aberta agora, e como ela opera.")
public record OperacaoAtualResponse(
        @Schema(description = "A loja sobre a qual esta resposta fala.")
        UUID estabelecimentoId,
        @Schema(description = "Aberta agora: horário de funcionamento e pausa, compostos no merchant.")
        boolean aberto,
        @Schema(description = "O tipo de operação da loja.")
        TipoDeOperacao tipoDeOperacao
) {

    public static OperacaoAtualResponse de(OperacaoAtual operacao) {
        return new OperacaoAtualResponse(
                operacao.estabelecimentoId(), operacao.aberto(), operacao.tipoDeOperacao());
    }
}
