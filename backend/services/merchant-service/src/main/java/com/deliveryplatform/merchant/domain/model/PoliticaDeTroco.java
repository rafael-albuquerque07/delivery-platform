package com.deliveryplatform.merchant.domain.model;

import com.deliveryplatform.valuetypes.Money;

import java.util.Objects;

/**
 * Quanto de troco a loja consegue dar, e o que fazer quando não consegue
 * (`estabelecimento.md` §4, "Política de troco"). Fecha a ponta solta que
 * {@code pedido.md} I4 deixou.
 *
 * <p><b>Este é o primeiro consumidor do {@code :value-types}.</b> A ADR-040
 * registrou como consequência negativa que o módulo nascia sem consumidor —
 * <i>"módulo compartilhado que ninguém consome é peça que não rodou, e o prazo
 * para isso deixar de ser verdade é uma rodada"</i>. É esta.
 *
 * <p><b>Não há método {@code aceita(trocoPara, total)} aqui, e a ausência é
 * decisão.</b> A regra completa é:
 *
 * <pre>
 * trocoDevido = trocoPara − total
 *
 * trocoDevido &gt; fundoMaximoDeTroco  ∧  ¬aceitaPedidoSemTrocoDisponivel
 *     → pedido RECUSADO, dizendo quanto a loja consegue dar
 *
 * trocoDevido &gt; fundoMaximoDeTroco  ∧   aceitaPedidoSemTrocoDisponivel
 *     → pedido ACEITO, com aviso ao cliente e ao entregador
 * </pre>
 *
 * <p>Só que ela é avaliada no fechamento do pedido, que acontece no
 * {@code order-service} — e o {@code order-service} não pode importar código
 * deste serviço (ADR-001). Um método aqui não teria chamador nenhum, e método
 * disponível é método que um dia é usado: o primeiro que o chamasse estaria
 * recompondo, dentro do {@code merchant}, uma decisão que pertence ao pedido.
 * O que atravessa a fronteira são os <b>dois valores</b>, por porta ou por
 * evento; a conta é de quem fecha o pedido.
 *
 * <p>Zero é valor válido e significa loja sem fundo de troco — não ausência de
 * configuração, exatamente como o pedido mínimo zero de {@code estabelecimento.md}
 * §4. O que não é válido é negativo: dívida de troco não existe.
 */
public record PoliticaDeTroco(Money fundoMaximoDeTroco, boolean aceitaPedidoSemTrocoDisponivel) {

    public PoliticaDeTroco {
        Objects.requireNonNull(fundoMaximoDeTroco, "fundoMaximoDeTroco");
        if (fundoMaximoDeTroco.ehNegativo()) {
            throw new IllegalArgumentException(
                    "fundo máximo de troco não pode ser negativo: " + fundoMaximoDeTroco);
        }
    }
}
