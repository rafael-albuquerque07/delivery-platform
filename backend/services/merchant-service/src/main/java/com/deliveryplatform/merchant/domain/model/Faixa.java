package com.deliveryplatform.merchant.domain.model;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Um turno de funcionamento (`estabelecimento.md` §4). Hora civil, no fuso do
 * estabelecimento — nunca instante.
 *
 * <p><b>A faixa que cruza a meia-noite é o bug clássico, e aqui ela é regra.</b>
 * Com {@code fim < inicio}, a faixa pertence ao dia de <b>início</b> e se estende
 * ao seguinte: terça 18:00–02:00 significa "de terça às 18h até quarta às 2h", e
 * às 00:30 de quarta a loja está aberta <i>pela faixa de terça</i>. Quem
 * responde isso são os dois métodos abaixo, e é por isso que eles vêm em par.
 *
 * <p><b>Início inclusivo, fim exclusivo.</b> Às 18:00 em ponto a loja abriu; às
 * 02:00 em ponto ela já fechou. Sem essa escolha, uma faixa 18:00–02:00 e outra
 * 02:00–06:00 se sobreporiam num minuto, e o pedido daquele minuto passaria por
 * duas regras.
 *
 * <p><b>Segundos são truncados.</b> O comerciante escolhe hora num relógio, e
 * uma faixa terminando às 02:00:30 é um defeito que ninguém consegue ver na
 * tela. É normalização, do mesmo tipo que a escala 2 do {@code Money}.
 */
public record Faixa(LocalTime inicio, LocalTime fim) implements Comparable<Faixa> {

    public Faixa {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        inicio = inicio.truncatedTo(ChronoUnit.MINUTES);
        fim = fim.truncatedTo(ChronoUnit.MINUTES);
        if (inicio.equals(fim)) {
            throw new IllegalArgumentException(
                    "faixa de início igual ao fim é ambígua — nem turno vazio nem "
                            + "vinte e quatro horas: " + inicio);
        }
    }

    public static Faixa de(String inicio, String fim) {
        return new Faixa(LocalTime.parse(inicio), LocalTime.parse(fim));
    }

    /** {@code fim < inicio}: o turno atravessa a meia-noite. */
    public boolean cruzaMeiaNoite() {
        return fim.isBefore(inicio);
    }

    /**
     * A faixa cobre esta hora <b>no dia a que ela pertence</b>. Para a faixa que
     * cruza a meia-noite, é só a metade antes das 24:00.
     */
    public boolean cobreNoDiaDeInicio(LocalTime hora) {
        return cruzaMeiaNoite()
                ? !hora.isBefore(inicio)
                : !hora.isBefore(inicio) && hora.isBefore(fim);
    }

    /**
     * A faixa cobre esta hora <b>no dia seguinte ao dela</b> — a metade depois da
     * meia-noite. Faixa que não cruza nunca cobre o dia seguinte.
     */
    public boolean cobreNoDiaSeguinte(LocalTime hora) {
        return cruzaMeiaNoite() && hora.isBefore(fim);
    }

    /** Ordem de exibição: almoço antes de jantar. */
    @Override
    public int compareTo(Faixa outra) {
        int porInicio = inicio.compareTo(outra.inicio);
        return porInicio != 0 ? porInicio : fim.compareTo(outra.fim);
    }

    @Override
    public String toString() {
        return "%s–%s".formatted(inicio, fim);
    }
}
