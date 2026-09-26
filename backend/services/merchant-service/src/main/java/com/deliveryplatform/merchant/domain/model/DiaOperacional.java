package com.deliveryplatform.merchant.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * O dia operacional da ADR-025, implementado um mês depois de decidido.
 *
 * <pre>
 * HORA_DE_CORTE = 04:00
 *
 * diaOperacional(instante, fuso):
 *     local = instante convertido para fuso
 *     se local.hora &lt; 04:00  →  local.data − 1 dia
 *     senão                  →  local.data
 * </pre>
 *
 * <p>Uma venda à 01:30 de domingo pertence ao dia operacional de <b>sábado</b>,
 * que é como o comerciante fala e como ele confere.
 *
 * <p><b>Por que devolve {@code LocalDate} e não um tipo próprio.</b> O
 * {@code Money} embrulha o {@code BigDecimal} porque a aritmética do
 * {@code BigDecimal} é errada por omissão — escala e arredondamento precisam de
 * dono. A aritmética de {@code LocalDate} não é errada; o que é particular aqui
 * é a <i>derivação</i>, e ela mora nesta função. Embrulhar acrescentaria um
 * desembrulho em toda fronteira — payload, coluna, comparação — em troca de
 * nada.
 *
 * <p><b>Por que mora no {@code merchant}.</b> Porque é aqui que está o
 * {@code fusoHorario}, e porque continua havendo <b>um</b> serviço que calcula.
 * O {@code catalog} recebe o valor no evento e pela porta; ele <i>compara</i>,
 * nunca calcula (ADR-046 §6). O gatilho para a função mudar de lugar continua
 * sendo o segundo serviço que precise <b>calcular</b> — o {@code settlement},
 * que congela o dia na abertura da jornada, ou o {@code order}.
 */
public final class DiaOperacional {

    /**
     * Constante de domínio, igual para todas as lojas.
     *
     * <p>A ADR-025 recusou torná-la um campo por loja: um comerciante que a
     * configurasse errado produziria um fechamento que não bate com o dia que
     * ele conta de cabeça, e o defeito apareceria no extrato, semanas depois.
     */
    public static final LocalTime HORA_DE_CORTE = LocalTime.of(4, 0);

    private DiaOperacional() {
    }

    /**
     * O dia operacional a que um instante pertence, no fuso da loja.
     *
     * @param instante momento em UTC — nunca hora civil
     * @param fuso     o {@code fusoHorario} do estabelecimento (M16 garante que é válido)
     */
    public static LocalDate de(Instant instante, FusoHorario fuso) {
        LocalDateTime local = LocalDateTime.ofInstant(instante, fuso.zona());

        // Estritamente menor: às 04:00 em ponto o dia novo começou. É a mesma
        // convenção de início inclusivo e fim exclusivo que a faixa de horário
        // usa (estabelecimento.md §4) — sem ela, o instante 04:00 pertenceria a
        // dois dias operacionais e o total do dia contaria a mesma venda duas
        // vezes.
        return local.toLocalTime().isBefore(HORA_DE_CORTE)
                ? local.toLocalDate().minusDays(1)
                : local.toLocalDate();
    }
}
