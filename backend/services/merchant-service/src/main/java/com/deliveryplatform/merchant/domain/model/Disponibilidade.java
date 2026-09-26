package com.deliveryplatform.merchant.domain.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Quando a loja atende (`estabelecimento.md` §4). Reúne o horário de
 * funcionamento e a pausa, porque a pergunta que o resto do sistema faz é uma
 * só — <i>"está aberta agora?"</i> — e a resposta depende das duas.
 *
 * <p><b>É aqui que a regra da meia-noite mora, e em lugar nenhum mais.</b> O
 * {@code order} e o {@code conversation} perguntam pela
 * {@code OperacaoDoEstabelecimentoPort} e recebem um booleano; recompor a regra
 * do lado de fora é o que o documento de domínio proíbe com todas as letras.
 *
 * <p><b>{@code DayOfWeek} do JDK, não um enum próprio.</b> A ADR-035 manda o
 * domínio em português, e a exceção é padrão de engenharia — {@code Instant},
 * {@code LocalTime} e {@code DayOfWeek} entram pelo mesmo motivo que
 * {@code Money} fica em inglês. Escrever um {@code DiaDaSemana} idêntico
 * custaria conversão em toda fronteira e uma tabela de equivalência para
 * manter.
 *
 * <p><b>Horário vazio é válido e significa "nunca abre por horário".</b> É o
 * estado de uma loja recém-cadastrada, antes de o comerciante preencher a tela.
 * Não é erro — e o aceite manual fora do horário (T02) continua sendo o caminho
 * para atender assim mesmo, do lado do {@code order}.
 */
public record Disponibilidade(Map<DayOfWeek, List<Faixa>> horarioDeFuncionamento, Pausa pausa) {

    public Disponibilidade {
        Objects.requireNonNull(horarioDeFuncionamento, "horarioDeFuncionamento");
        Objects.requireNonNull(pausa, "pausa");
        horarioDeFuncionamento = normalizar(horarioDeFuncionamento);
    }

    /** Loja recém-cadastrada: sem horário e sem pausa. */
    public static Disponibilidade semHorario() {
        return new Disponibilidade(Map.of(), Pausa.nenhuma());
    }

    public Disponibilidade com(Pausa novaPausa) {
        return new Disponibilidade(horarioDeFuncionamento, novaPausa);
    }

    /** Vazio quando o dia não tem turno — nunca nulo. */
    public List<Faixa> faixasDe(DayOfWeek dia) {
        return horarioDeFuncionamento.getOrDefault(dia, List.of());
    }

    /**
     * A pergunta que o resto do sistema faz. Horário <b>e</b> pausa, compostos
     * aqui.
     */
    public boolean abertaEm(Instant agora, FusoHorario fuso) {
        Objects.requireNonNull(agora, "agora");
        Objects.requireNonNull(fuso, "fuso");
        return dentroDoHorario(agora, fuso) && !pausa.ativaEm(agora);
    }

    /**
     * Só o horário, sem a pausa. A conversão para tempo civil acontece uma vez,
     * aqui, com a zona explícita — nunca {@code LocalDateTime.now()} (ADR-025).
     *
     * <p>O dia anterior é consultado porque a faixa que cruza a meia-noite
     * pertence ao dia de início. Isso é aritmética de <b>calendário</b> sobre o
     * dia já convertido, não aritmética sobre hora local: com horário de verão,
     * uma faixa encurta ou alonga uma hora naquele dia e nada quebra — é o que a
     * ADR-025 §6 registra.
     */
    public boolean dentroDoHorario(Instant agora, FusoHorario fuso) {
        return inicioDaFaixaLocal(agora, fuso).isPresent();
    }

    /**
     * O instante em que começou a faixa que contém {@code agora}; vazio fora do
     * horário.
     *
     * <p>É daqui que sai o expediente (ADR-046, emendada): o dia operacional do
     * <b>início</b> da faixa, e não do instante. Uma loja 22:00–06:00 às 04:30
     * está no expediente que abriu às 22h da véspera — o dia operacional do
     * instante já virou às 04:00, e usá-lo abriria um segundo expediente no meio
     * do turno.
     *
     * <p>Faixas podem se sobrepor (§4 permite). Quando mais de uma contém o
     * instante, vale a de <b>início mais antigo</b>: é a que já estava aberta, e
     * a resposta não depende da ordem em que as faixas foram cadastradas.
     */
    public Optional<Instant> inicioDaFaixaEm(Instant agora, FusoHorario fuso) {
        return inicioDaFaixaLocal(agora, fuso)
                .map(inicio -> inicio.atZone(fuso.zona()).toInstant());
    }

    /**
     * O percurso único das faixas, de que {@link #dentroDoHorario} e
     * {@link #inicioDaFaixaEm} saem. Faixa do dia anterior que cobre o dia
     * seguinte começou ontem, e portanto sempre antes de qualquer faixa de hoje
     * — mas a comparação é feita assim mesmo, em vez de confiar na ordem dos
     * laços.
     */
    private Optional<LocalDateTime> inicioDaFaixaLocal(Instant agora, FusoHorario fuso) {
        Objects.requireNonNull(agora, "agora");
        Objects.requireNonNull(fuso, "fuso");
        LocalDateTime local = LocalDateTime.ofInstant(agora, fuso.zona());
        LocalDate hoje = local.toLocalDate();
        LocalTime hora = local.toLocalTime();

        LocalDateTime maisAntigo = null;
        for (Faixa faixa : faixasDe(hoje.getDayOfWeek())) {
            if (faixa.cobreNoDiaDeInicio(hora)) {
                maisAntigo = oMaisAntigo(maisAntigo, hoje.atTime(faixa.inicio()));
            }
        }
        LocalDate ontem = hoje.minusDays(1);
        for (Faixa faixa : faixasDe(ontem.getDayOfWeek())) {
            if (faixa.cobreNoDiaSeguinte(hora)) {
                maisAntigo = oMaisAntigo(maisAntigo, ontem.atTime(faixa.inicio()));
            }
        }
        return Optional.ofNullable(maisAntigo);
    }

    private static LocalDateTime oMaisAntigo(LocalDateTime atual, LocalDateTime candidato) {
        return atual == null || candidato.isBefore(atual) ? candidato : atual;
    }

    /**
     * Ordena as faixas de cada dia por início e recusa faixa repetida. Duas
     * faixas idênticas no mesmo dia não significam nada, e a chave primária da
     * tabela também não as aceitaria — o domínio e o banco precisam concordar
     * sobre o que é um horário válido.
     *
     * <p>Faixas que se <b>sobrepõem</b> sem serem idênticas continuam
     * permitidas: "aberta" é um OU sobre as faixas, então sobreposição é
     * redundância, não contradição. Recusá-la exigiria uma regra sobre
     * sobreposição entre faixas que cruzam a meia-noite, e nenhum documento
     * pediu isso.
     */
    private static Map<DayOfWeek, List<Faixa>> normalizar(Map<DayOfWeek, List<Faixa>> origem) {
        Map<DayOfWeek, List<Faixa>> copia = new EnumMap<>(DayOfWeek.class);
        origem.forEach((dia, faixas) -> {
            Objects.requireNonNull(dia, "dia da semana");
            Objects.requireNonNull(faixas, "faixas de " + dia);
            if (faixas.isEmpty()) {
                return;
            }
            TreeSet<Faixa> ordenadas = new TreeSet<>(faixas);
            if (ordenadas.size() != faixas.size()) {
                throw new IllegalArgumentException("faixa repetida em " + dia + ": " + faixas);
            }
            copia.put(dia, List.copyOf(new ArrayList<>(ordenadas)));
        });
        return Map.copyOf(copia);
    }
}
