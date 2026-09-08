package com.deliveryplatform.identity.application.port.out;

import java.time.Instant;

/**
 * O token e as duas pontas da sua validade.
 *
 * <p>Carrega {@code emitidoEm} além de {@code expiraEm} para que o
 * {@code expiresIn} da resposta seja a duração decidida na ADR-037 — trinta
 * minutos exatos — e não a diferença até o relógio do controller, que daria
 * 1799 e faria o teste afirmar uma faixa em vez de um número.
 */
public record TokenEmitido(String valor, Instant emitidoEm, Instant expiraEm) {

    public long validadeEmSegundos() {
        return expiraEm.getEpochSecond() - emitidoEm.getEpochSecond();
    }
}
