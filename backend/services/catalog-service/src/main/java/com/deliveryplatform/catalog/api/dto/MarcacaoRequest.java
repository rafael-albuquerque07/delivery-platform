package com.deliveryplatform.catalog.api.dto;

import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * O corpo da marcação: um estado, e nada mais.
 *
 * <p><b>Não há {@code marcadoEm} nem {@code expedienteDeReferencia} aqui</b>, e
 * a ausência é a decisão. Quem escolhe a data é o servidor: o instante pelo
 * relógio do serviço, o expediente perguntado ao {@code merchant}. Um cliente
 * que pudesse escolher o expediente poderia fazer um produto voltar ao cardápio
 * na próxima abertura — ou nunca mais.
 *
 * <p>É a mesma família da invariante 2 do {@code CLAUDE.md}, que manda ignorar
 * valor que venha do cliente: aqui não há o que ignorar, porque o campo não
 * existe no contrato.
 *
 * <p><b>Valor desconhecido no enum é 400</b>, e isso é correto para uma
 * requisição: a ADR-027 §2 trata de <i>eventos</i> entre serviços, onde o
 * consumidor tolera o que não conhece. Um corpo HTTP é a borda com o cliente, e
 * aceitar o que não se entende ali é aceitar escrever lixo no banco.
 */
@Schema(description = "O novo estado de disponibilidade. O carimbo é do servidor.")
public record MarcacaoRequest(

        @Schema(description = "DISPONIVEL, ACABANDO, ESGOTADO_HOJE ou ESGOTADO_INDETERMINADO.",
                example = "ESGOTADO_HOJE")
        @NotNull(message = "estado é obrigatório")
        EstadoDeDisponibilidade estado
) {
}
