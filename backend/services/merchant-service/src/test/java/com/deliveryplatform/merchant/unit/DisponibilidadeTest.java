package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.domain.model.Disponibilidade;
import com.deliveryplatform.merchant.domain.model.Faixa;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.domain.model.Pausa;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.deliveryplatform.merchant.support.LojaDeTeste.emSaoPaulo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 15/09/2026 é terça e 16/09/2026 é quarta — as datas foram escolhidas por
 * isso, e os nomes dos dias aparecem nos testes para que uma falha seja legível
 * sem calendário na mão.
 */
class DisponibilidadeTest {

    private static final FusoHorario SP = FusoHorario.PADRAO;
    private static final Disponibilidade PIZZARIA = LojaDeTeste.disponibilidade();

    // ── a faixa que cruza a meia-noite ──────────────────────────────────────

    @Test
    void a_pizzaria_esta_aberta_a_uma_da_manha_de_quarta() {
        assertThat(PIZZARIA.dentroDoHorario(emSaoPaulo("2026-09-16T01:00"), SP))
                .as("terça 18:00–02:00 pertence ao dia de início e se estende ao "
                        + "seguinte — à 01:00 de quarta a loja está aberta PELA FAIXA "
                        + "DE TERÇA. `estabelecimento.md` §4 chama este teste de "
                        + "obrigatório")
                .isTrue();
        assertThat(PIZZARIA.dentroDoHorario(emSaoPaulo("2026-09-16T00:00"), SP)).isTrue();
        assertThat(PIZZARIA.dentroDoHorario(emSaoPaulo("2026-09-16T01:59"), SP)).isTrue();
    }

    @Test
    void as_duas_da_manha_ja_fechou_porque_o_fim_e_exclusivo() {
        assertThat(PIZZARIA.dentroDoHorario(emSaoPaulo("2026-09-16T02:00"), SP))
                .as("fim exclusivo: sem isso, 18:00–02:00 e 02:00–06:00 se "
                        + "sobreporiam num minuto")
                .isFalse();
    }

    @Test
    void as_dezoito_em_ponto_de_terca_abre_e_as_dezessete_e_cinquenta_e_nove_nao() {
        assertThat(PIZZARIA.dentroDoHorario(emSaoPaulo("2026-09-15T17:59"), SP)).isFalse();
        assertThat(PIZZARIA.dentroDoHorario(emSaoPaulo("2026-09-15T18:00"), SP))
                .as("início inclusivo")
                .isTrue();
        assertThat(PIZZARIA.dentroDoHorario(emSaoPaulo("2026-09-15T23:59"), SP)).isTrue();
    }

    @Test
    void quarta_as_dezoito_esta_fechada_porque_a_faixa_e_de_terca() {
        assertThat(PIZZARIA.dentroDoHorario(emSaoPaulo("2026-09-16T18:00"), SP))
                .as("a faixa pertence ao dia de início; quarta não tem turno")
                .isFalse();
    }

    // ── o fuso decide ───────────────────────────────────────────────────────

    @Test
    void o_mesmo_instante_abre_em_manaus_e_nao_em_sao_paulo() {
        Instant instante = Instant.parse("2026-09-16T05:30:00Z");

        assertThat(PIZZARIA.dentroDoHorario(instante, SP))
                .as("02:30 em São Paulo — passou das 02:00")
                .isFalse();
        assertThat(PIZZARIA.dentroDoHorario(instante, FusoHorario.de("America/Manaus")))
                .as("01:30 em Manaus — ainda dentro da faixa de terça. É por isso "
                        + "que M16 exige o fuso: uma hora de diferença decide se a "
                        + "loja aceita o pedido")
                .isTrue();
    }

    // ── turnos ──────────────────────────────────────────────────────────────

    @Test
    void almoco_e_jantar_sao_turnos_separados_e_entre_eles_fecha() {
        assertThat(PIZZARIA.dentroDoHorario(emSaoPaulo("2026-09-19T12:00"), SP)).isTrue();
        assertThat(PIZZARIA.dentroDoHorario(emSaoPaulo("2026-09-19T16:00"), SP))
                .as("sábado, entre o almoço e o jantar")
                .isFalse();
        assertThat(PIZZARIA.dentroDoHorario(emSaoPaulo("2026-09-19T20:00"), SP)).isTrue();
    }

    @Test
    void as_faixas_do_dia_saem_ordenadas_por_inicio() {
        Map<DayOfWeek, List<Faixa>> foraDeOrdem = new EnumMap<>(DayOfWeek.class);
        foraDeOrdem.put(
                DayOfWeek.MONDAY, List.of(Faixa.de("18:00", "23:00"), Faixa.de("11:00", "14:00")));

        Disponibilidade padaria = new Disponibilidade(foraDeOrdem, Pausa.nenhuma());

        assertThat(padaria.faixasDe(DayOfWeek.MONDAY))
                .as("ordem de exibição: almoço antes de jantar")
                .containsExactly(Faixa.de("11:00", "14:00"), Faixa.de("18:00", "23:00"));
    }

    @Test
    void loja_sem_horario_nunca_abre_por_horario() {
        assertThat(Disponibilidade.semHorario().dentroDoHorario(Instant.now(), SP))
                .as("é o estado de uma loja recém-cadastrada — não é erro, e o "
                        + "aceite manual de T02 continua sendo o caminho")
                .isFalse();
        assertThat(Disponibilidade.semHorario().horarioDeFuncionamento()).isEmpty();
    }

    @Test
    void dia_com_lista_vazia_nao_entra_no_mapa() {
        Disponibilidade vazia =
                new Disponibilidade(Map.of(DayOfWeek.SUNDAY, List.of()), Pausa.nenhuma());

        assertThat(vazia.horarioDeFuncionamento()).isEmpty();
        assertThat(vazia.faixasDe(DayOfWeek.SUNDAY)).isEmpty();
    }

    @Test
    void faixas_que_se_sobrepoem_sao_permitidas() {
        Map<DayOfWeek, List<Faixa>> sobrepostas = new EnumMap<>(DayOfWeek.class);
        sobrepostas.put(
                DayOfWeek.FRIDAY, List.of(Faixa.de("11:00", "15:00"), Faixa.de("14:00", "18:00")));

        Disponibilidade disponibilidade = new Disponibilidade(sobrepostas, Pausa.nenhuma());

        assertThat(disponibilidade.dentroDoHorario(emSaoPaulo("2026-09-18T14:30"), SP))
                .as("\"aberta\" é um OU sobre as faixas: sobrepor é redundância, "
                        + "não contradição")
                .isTrue();
    }

    // ── pausa ───────────────────────────────────────────────────────────────

    @Test
    void pausa_indefinida_fecha_a_loja_dentro_da_faixa() {
        Instant dentroDaFaixa = emSaoPaulo("2026-09-15T20:00");

        assertThat(PIZZARIA.abertaEm(dentroDaFaixa, SP)).isTrue();
        assertThat(PIZZARIA.com(Pausa.indefinida("cozinha atolou")).abertaEm(dentroDaFaixa, SP))
                .isFalse();
    }

    @Test
    void pausa_com_prazo_no_futuro_fecha_e_com_prazo_vencido_nao_fecha_mais() {
        Disponibilidade ate21 =
                PIZZARIA.com(Pausa.ate(emSaoPaulo("2026-09-15T21:00"), "fila grande"));

        assertThat(ate21.abertaEm(emSaoPaulo("2026-09-15T20:00"), SP)).isFalse();
        assertThat(ate21.abertaEm(emSaoPaulo("2026-09-15T21:30"), SP))
                .as("pausa vencida não precisa de faxina: o registro diz o que foi "
                        + "feito, o cálculo diz o que vale agora")
                .isTrue();
    }

    @Test
    void pausa_nao_muda_o_horario_so_o_resultado() {
        Instant foraDaFaixa = emSaoPaulo("2026-09-15T15:00");
        Disponibilidade pausada = PIZZARIA.com(Pausa.indefinida("reforma"));

        assertThat(pausada.dentroDoHorario(foraDaFaixa, SP)).isFalse();
        assertThat(pausada.horarioDeFuncionamento())
                .isEqualTo(PIZZARIA.horarioDeFuncionamento());
    }

    @Test
    void pausa_ativa_sem_motivo_e_recusada() {
        assertThatThrownBy(() -> Pausa.indefinida("   "))
                .as("pausa sem motivo é invisível para quem olha depois — inclusive "
                        + "para o comerciante na manhã seguinte")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pausa.ate(Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pausa_inativa_nao_carrega_prazo_nem_motivo() {
        assertThatThrownBy(() -> new Pausa(false, null, "sobrou da pausa anterior"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Pausa.nenhuma().ativaEm(Instant.now())).isFalse();
    }

    // ── Faixa ───────────────────────────────────────────────────────────────

    @Test
    void faixa_de_inicio_igual_ao_fim_e_recusada() {
        assertThatThrownBy(() -> Faixa.de("10:00", "10:00"))
                .as("nem turno vazio nem vinte e quatro horas — ambígua")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void segundos_sao_truncados() {
        assertThat(Faixa.de("18:00:45", "02:00:30"))
                .as("o comerciante escolhe hora num relógio; 02:00:30 é defeito "
                        + "que ninguém vê na tela")
                .isEqualTo(Faixa.de("18:00", "02:00"));
    }

    @Test
    void cruza_meia_noite_e_quem_tem_fim_antes_do_inicio() {
        assertThat(Faixa.de("18:00", "02:00").cruzaMeiaNoite()).isTrue();
        assertThat(Faixa.de("11:00", "14:00").cruzaMeiaNoite()).isFalse();
    }

    @Test
    void faixa_repetida_no_mesmo_dia_e_recusada() {
        Map<DayOfWeek, List<Faixa>> repetida = new EnumMap<>(DayOfWeek.class);
        repetida.put(
                DayOfWeek.FRIDAY, List.of(Faixa.de("11:00", "14:00"), Faixa.de("11:00", "14:00")));

        assertThatThrownBy(() -> new Disponibilidade(repetida, Pausa.nenhuma()))
                .as("a chave primária da tabela também não aceitaria — domínio e "
                        + "banco precisam concordar sobre o que é horário válido")
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── o agregado compõe ───────────────────────────────────────────────────

    @Test
    void o_estabelecimento_compoe_horario_pausa_e_o_proprio_fuso() {
        assertThat(LojaDeTeste.pizzaria().estaAberta(emSaoPaulo("2026-09-16T01:00")))
                .as("quem pergunta não recompõe a regra da meia-noite: ela mora "
                        + "num lugar só, e o agregado junta o fuso da identificação "
                        + "com o horário da disponibilidade")
                .isTrue();
        assertThat(LojaDeTeste.pizzaria().estaAberta(emSaoPaulo("2026-09-16T02:00"))).isFalse();
    }

    @Test
    void a_data_dos_testes_e_mesmo_terca_e_quarta() {
        assertThat(LocalDateTime.parse("2026-09-15T00:00").getDayOfWeek())
                .isEqualTo(DayOfWeek.TUESDAY);
        assertThat(LocalDateTime.parse("2026-09-16T00:00").getDayOfWeek())
                .isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(LocalDateTime.parse("2026-09-19T00:00").getDayOfWeek())
                .isEqualTo(DayOfWeek.SATURDAY);
    }
}
