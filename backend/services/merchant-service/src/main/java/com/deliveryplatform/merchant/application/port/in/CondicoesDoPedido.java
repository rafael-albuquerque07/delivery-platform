package com.deliveryplatform.merchant.application.port.in;

import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.PoliticaDeTroco;
import com.deliveryplatform.valuetypes.Money;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * O que a T01 congela, vindo do {@code merchant}, para uma modalidade — e um endereço, se for
 * entrega (ADR-087). É o lado do {@code merchant} da {@code DeliveryQuotePort} da ADR-020.
 *
 * <p>Quatro formas, e cada uma tem o seu construtor:
 *
 * <ul>
 *   <li><b>a loja não aceita a modalidade</b> — {@code aceita} falso e nada mais;</li>
 *   <li><b>retirada</b> — métodos, pedido mínimo, troco e o desconto de retirada;</li>
 *   <li><b>entrega atendida</b> — métodos, mínimo, troco, sem desconto (I12), e a área com a
 *       taxa. <b>Taxa zero é taxa</b>: a área entrega de graça;</li>
 *   <li><b>entrega não atendida</b> — métodos, mínimo, troco, <b>sem taxa</b> (M11), e os nomes
 *       das áreas ativas, para a recusa dizer onde a loja entrega (ADR-020).</li>
 * </ul>
 *
 * <p>Os campos que não se aplicam a uma forma são nulos aqui, e ausentes na resposta. O pedido
 * mínimo nunca é nulo numa modalidade aceita: a M17 dá entrada a toda modalidade aceita, e zero é
 * sem mínimo.
 */
public record CondicoesDoPedido(
        UUID estabelecimentoId,
        Modalidade modalidade,
        boolean aceita,
        Set<MetodoPagamento> metodos,
        Money pedidoMinimo,
        Money descontoDeRetirada,
        PoliticaDeTroco politicaDeTroco,
        Boolean atendida,
        String area,
        Money taxaDeEntrega,
        List<String> bairrosAtendidos) {

    public CondicoesDoPedido {
        Objects.requireNonNull(estabelecimentoId, "estabelecimentoId");
        Objects.requireNonNull(modalidade, "modalidade");
        metodos = metodos == null ? null : Set.copyOf(metodos);
        bairrosAtendidos = bairrosAtendidos == null ? null : List.copyOf(bairrosAtendidos);
    }

    public static CondicoesDoPedido naoAceita(UUID loja, Modalidade modalidade) {
        return new CondicoesDoPedido(loja, modalidade, false,
                null, null, null, null, null, null, null, null);
    }

    public static CondicoesDoPedido retirada(UUID loja, Set<MetodoPagamento> metodos, Money minimo,
                                             Money desconto, PoliticaDeTroco troco) {
        return new CondicoesDoPedido(loja, Modalidade.RETIRADA, true,
                metodos, minimo, desconto, troco, null, null, null, null);
    }

    public static CondicoesDoPedido entregaAtendida(UUID loja, Set<MetodoPagamento> metodos,
                                                    Money minimo, PoliticaDeTroco troco,
                                                    String area, Money taxa) {
        return new CondicoesDoPedido(loja, Modalidade.ENTREGA, true,
                metodos, minimo, null, troco, true, area, Objects.requireNonNull(taxa, "taxa"), null);
    }

    public static CondicoesDoPedido entregaNaoAtendida(UUID loja, Set<MetodoPagamento> metodos,
                                                       Money minimo, PoliticaDeTroco troco,
                                                       List<String> bairrosAtendidos) {
        return new CondicoesDoPedido(loja, Modalidade.ENTREGA, true,
                metodos, minimo, null, troco, false, null, null, bairrosAtendidos);
    }
}
