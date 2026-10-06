package com.deliveryplatform.catalog.application.exception;

/**
 * Pediram {@code ESGOTADO_HOJE} numa loja que não abre por horário.
 *
 * <p>{@code ESGOTADO_HOJE} significa "volta na abertura do próximo expediente".
 * Numa loja sem horário <b>não há próximo expediente</b> — a ADR-046 já
 * registrava: <i>"Loja sem horário nunca abre expediente, e portanto nunca
 * reativa nada"</i>. O produto ficaria esgotado para sempre, sem erro em lugar
 * nenhum.
 *
 * <p>É 409, pelo mesmo critério do {@code catalogo.md} §5: estado do mundo, não
 * erro do chamador. Ele pediu uma coisa legítima; a loja é que não tem o que ela
 * pressupõe.
 *
 * <p>O detalhe da resposta diz o que fazer, porque existe caminho:
 * {@code ESGOTADO_INDETERMINADO} não depende de expediente, e é exatamente o
 * que uma loja assim precisa.
 */
public class LojaSemExpediente extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LojaSemExpediente() {
        super("esta loja não abre por horário: ESGOTADO_HOJE nunca reativaria. "
                + "Use ESGOTADO_INDETERMINADO.");
    }
}
