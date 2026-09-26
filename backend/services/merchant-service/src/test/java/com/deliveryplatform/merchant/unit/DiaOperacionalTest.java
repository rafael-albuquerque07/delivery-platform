package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.model.DiaOperacional;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A ADR-025 decidiu isto em 24/08 e nada implementou até hoje.
 *
 * <p>Os casos aqui são os que a própria ADR usa para justificar a decisão — e
 * o último é o que prova que o fuso é da loja, não do servidor.
 */
class DiaOperacionalTest {

    private static final FusoHorario SAO_PAULO = FusoHorario.de("America/Sao_Paulo");
    private static final FusoHorario MANAUS = FusoHorario.de("America/Manaus");

    private static Instant civilEm(FusoHorario fuso, int ano, int mes, int dia, int hora, int minuto) {
        return ZonedDateTime.of(ano, mes, dia, hora, minuto, 0, 0, fuso.zona()).toInstant();
    }

    @Test
    @DisplayName("a venda da madrugada pertence ao dia anterior — o caso que a ADR-025 conta")
    void uma_e_meia_de_domingo_e_sabado() {
        Instant umaEMeiaDeDomingo = civilEm(SAO_PAULO, 2026, 9, 27, 1, 30);

        assertThat(DiaOperacional.de(umaEMeiaDeDomingo, SAO_PAULO))
                .as("é como o comerciante fala e como ele confere o caixa")
                .isEqualTo(LocalDate.of(2026, 9, 26));
    }

    @Test
    @DisplayName("às 04:00 em ponto o dia novo já começou")
    void a_hora_de_corte_e_inclusiva_no_dia_novo() {
        assertThat(DiaOperacional.de(civilEm(SAO_PAULO, 2026, 9, 27, 4, 0), SAO_PAULO))
                .as("início inclusivo, como a faixa de horário — sem isso o instante "
                        + "04:00 pertenceria a dois dias e a venda contaria duas vezes")
                .isEqualTo(LocalDate.of(2026, 9, 27));

        assertThat(DiaOperacional.de(civilEm(SAO_PAULO, 2026, 9, 27, 3, 59), SAO_PAULO))
                .isEqualTo(LocalDate.of(2026, 9, 26));
    }

    @Test
    void meio_dia_e_o_proprio_dia() {
        assertThat(DiaOperacional.de(civilEm(SAO_PAULO, 2026, 9, 26, 12, 0), SAO_PAULO))
                .isEqualTo(LocalDate.of(2026, 9, 26));
    }

    @Test
    void a_virada_da_meia_noite_nao_vira_o_dia_operacional() {
        // 23:50 e 00:10 são o mesmo expediente, e é esta a razão de a hora de
        // corte existir: a pizzaria que fecha às 2h não fecha o caixa à
        // meia-noite.
        LocalDate antes = DiaOperacional.de(civilEm(SAO_PAULO, 2026, 9, 26, 23, 50), SAO_PAULO);
        LocalDate depois = DiaOperacional.de(civilEm(SAO_PAULO, 2026, 9, 27, 0, 10), SAO_PAULO);

        assertThat(depois).isEqualTo(antes).isEqualTo(LocalDate.of(2026, 9, 26));
    }

    @Test
    @DisplayName("o mesmo instante dá dias diferentes em São Paulo e Manaus")
    void o_fuso_e_da_loja_e_nao_do_servidor() {
        // 03:30 em São Paulo é 02:30 em Manaus — as duas antes do corte, e o
        // mesmo dia anterior. Uma hora depois elas divergem: 04:30 em São Paulo
        // já é dia novo, 03:30 em Manaus ainda não.
        Instant instante = civilEm(SAO_PAULO, 2026, 9, 27, 4, 30);

        assertThat(DiaOperacional.de(instante, SAO_PAULO)).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(DiaOperacional.de(instante, MANAUS))
                .as("M16 existe para isto: uma loja com fuso errado fecha o dia errado")
                .isEqualTo(LocalDate.of(2026, 9, 26));
    }
}
