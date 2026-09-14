package com.deliveryplatform.merchant.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Uma linha de {@code estabelecimento_horario}: um turno num dia da semana.
 *
 * <p><b>Trio plano, e não mapa de listas.</b> O domínio guarda
 * {@code Map<DayOfWeek, List<Faixa>>}, e JPA não persiste mapa de coleção
 * direto — mesma forma do {@code MetodoAceitoJpa}. A tabela guarda o trio; quem
 * reagrupa e reordena é o mapper.
 *
 * <p><b>{@code LocalTime} aqui é correto, e não contradiz a ADR-025.</b> A
 * proibição é sobre {@code LocalDateTime} sem zona num campo persistido — hora
 * <i>sem lugar</i>. Isto é hora civil de propósito: "abre às 18h" é uma
 * configuração do calendário da loja, e só vira instante quando alguém pergunta
 * se ela está aberta, com o {@code fusoHorario} na mão.
 */
@Embeddable
public class HorarioJpa {

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_da_semana", nullable = false, length = 12)
    private DayOfWeek diaDaSemana;

    @Column(name = "inicio", nullable = false)
    private LocalTime inicio;

    @Column(name = "fim", nullable = false)
    private LocalTime fim;

    protected HorarioJpa() {
        // exigido pelo JPA
    }

    public HorarioJpa(DayOfWeek diaDaSemana, LocalTime inicio, LocalTime fim) {
        this.diaDaSemana = diaDaSemana;
        this.inicio = inicio;
        this.fim = fim;
    }

    public DayOfWeek getDiaDaSemana() {
        return diaDaSemana;
    }

    public LocalTime getInicio() {
        return inicio;
    }

    public LocalTime getFim() {
        return fim;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof HorarioJpa horario
                && diaDaSemana == horario.diaDaSemana
                && Objects.equals(inicio, horario.inicio)
                && Objects.equals(fim, horario.fim);
    }

    @Override
    public int hashCode() {
        return Objects.hash(diaDaSemana, inicio, fim);
    }
}
