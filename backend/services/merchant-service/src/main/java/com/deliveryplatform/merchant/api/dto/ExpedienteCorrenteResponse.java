package com.deliveryplatform.merchant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

/**
 * O que o {@code catalog} precisa para carimbar, e nada mais.
 *
 * <h2>O que eu deliberadamente não pus aqui</h2>
 *
 * <p><b>Se a loja está aberta agora.</b> Quem responde isso é a
 * {@code OperacaoDoEstabelecimentoPort} do {@code estabelecimento.md} §3, que
 * compõe horário <i>e</i> pausa, na rota interna da operação desde a M3-1
 * (ADR-087). Pôr um {@code aberto} aqui criaria a segunda resposta para a mesma
 * pergunta, e as duas teriam de concordar para sempre.
 *
 * <p><b>De onde veio a data</b> — se do expediente em curso ou da próxima
 * abertura. É informação verdadeira e sem consumidor: o catálogo apenas compara
 * datas, e a comparação é a mesma nos dois casos. Superfície que nasce por
 * simetria e não por necessidade é o que a nota do {@code sort} desta página
 * registra como dívida.
 *
 * <p>O {@code estabelecimentoId} volta porque a resposta é sobre uma loja e o
 * chamador pode estar resolvendo várias — é o mesmo eco que o
 * {@code ContextoDeAcesso} faz.
 */
@Schema(description = "O expediente a carimbar numa marcação de disponibilidade.")
public record ExpedienteCorrenteResponse(

        @Schema(description = "A loja sobre a qual esta resposta fala.")
        UUID estabelecimentoId,

        @Schema(description = "O dia operacional a gravar como expedienteDeReferencia.",
                example = "2026-09-30")
        LocalDate expedienteDeReferencia
) {
}
