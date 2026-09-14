package com.deliveryplatform.merchant.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A parada manual (`estabelecimento.md` §4). Bloqueia pedido novo e
 * <b>não toca em pedido em andamento</b> — M14. A cozinha atolou, param de
 * entrar pedidos, e os trinta que já estão na fila seguem seu curso.
 *
 * <p><b>{@code pausadoAte} é {@code Instant}, e nulo significa indefinida.</b>
 * "Pausado até as 21h" é hora civil na tela; o que se grava é o instante
 * (ADR-025). Nulo é "até alguém reabrir".
 *
 * <p><b>Pausa vencida não precisa de faxina.</b> Uma pausa com {@code ativa} e
 * {@code pausadoAte} no passado continua registrada como o comerciante a
 * deixou, e {@link #ativaEm(Instant)} devolve {@code false}. O registro diz o
 * que foi feito; o cálculo diz o que vale agora. Nenhuma rotina precisa passar
 * limpando.
 *
 * <p><b>Motivo é obrigatório quando ativa</b>, e isso é decisão desta rodada, não
 * do documento. Pausa sem motivo é invisível para quem olha depois — inclusive
 * para o próprio comerciante na manhã seguinte, tentando entender por que não
 * entrou pedido.
 */
public record Pausa(boolean ativa, Instant pausadoAte, String motivo) {

    private static final Pausa NENHUMA = new Pausa(false, null, null);

    public Pausa {
        if (ativa) {
            if (motivo == null || motivo.isBlank()) {
                throw new IllegalArgumentException("pausa ativa exige motivo");
            }
            motivo = motivo.trim();
        } else {
            if (pausadoAte != null || motivo != null) {
                throw new IllegalArgumentException(
                        "pausa inativa não carrega prazo nem motivo — retomar apaga os dois");
            }
        }
    }

    /** A loja não está pausada. */
    public static Pausa nenhuma() {
        return NENHUMA;
    }

    /** Pausada até alguém reabrir. */
    public static Pausa indefinida(String motivo) {
        return new Pausa(true, null, motivo);
    }

    /** Pausada até um instante. */
    public static Pausa ate(Instant quando, String motivo) {
        return new Pausa(true, Objects.requireNonNull(quando, "quando"), motivo);
    }

    /** Vale agora? Pausa com prazo vencido não vale mais. */
    public boolean ativaEm(Instant agora) {
        Objects.requireNonNull(agora, "agora");
        return ativa && (pausadoAte == null || agora.isBefore(pausadoAte));
    }

    public boolean ehIndefinida() {
        return ativa && pausadoAte == null;
    }
}
