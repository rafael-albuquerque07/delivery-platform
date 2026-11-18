package com.deliveryplatform.merchant.api.dto;

import com.deliveryplatform.merchant.application.port.in.CondicoesDoPedido;
import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.valuetypes.Money;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * As condições do pedido (ADR-087). O dinheiro sai como as rotas de {@code /settings} o devolvem:
 * o valor do {@code Money}.
 *
 * <p><b>Campo que não se aplica não sai</b> — e é por isso o {@code NON_NULL}. Na entrega não
 * atendida, a {@code taxaDeEntrega} não existe na resposta, porque ausência de área não é taxa
 * zero (M11); uma taxa {@code 0} só sai de uma área atendida de graça.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "O que a T01 congela, vindo do merchant. Campo que não se aplica não sai.")
public record CondicoesDoPedidoResponse(
        UUID estabelecimentoId,
        Modalidade modalidade,
        @Schema(description = "A loja aceita esta modalidade. Falso: nenhum outro campo sai.")
        boolean aceita,
        Set<MetodoPagamento> metodos,
        @Schema(description = "O pedido mínimo da modalidade, sobre os itens. Zero é sem mínimo (M17).")
        BigDecimal pedidoMinimo,
        @Schema(description = "Só na retirada (I12).")
        BigDecimal descontoDeRetirada,
        PoliticaDeTrocoResponse politicaDeTroco,
        @Schema(description = "Só na entrega: o endereço está numa área ativa.")
        Boolean atendida,
        @Schema(description = "Só na entrega atendida: o nome da área.")
        String area,
        @Schema(description = "Só na entrega atendida. Zero é entrega grátis; na não atendida, não sai (M11).")
        BigDecimal taxaDeEntrega,
        @Schema(description = "Só na entrega não atendida: os nomes das áreas ativas (ADR-020).")
        List<String> bairrosAtendidos
) {

    public record PoliticaDeTrocoResponse(BigDecimal fundoMaximoDeTroco,
                                          boolean aceitaPedidoSemTrocoDisponivel) {
    }

    public static CondicoesDoPedidoResponse de(CondicoesDoPedido condicoes) {
        var troco = condicoes.politicaDeTroco();
        return new CondicoesDoPedidoResponse(
                condicoes.estabelecimentoId(),
                condicoes.modalidade(),
                condicoes.aceita(),
                condicoes.metodos(),
                valor(condicoes.pedidoMinimo()),
                valor(condicoes.descontoDeRetirada()),
                troco == null ? null : new PoliticaDeTrocoResponse(
                        troco.fundoMaximoDeTroco().valor(), troco.aceitaPedidoSemTrocoDisponivel()),
                condicoes.atendida(),
                condicoes.area(),
                valor(condicoes.taxaDeEntrega()),
                condicoes.bairrosAtendidos());
    }

    private static BigDecimal valor(Money dinheiro) {
        return dinheiro == null ? null : dinheiro.valor();
    }
}
