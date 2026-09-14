package com.deliveryplatform.merchant.support;

import com.deliveryplatform.merchant.domain.model.AreaDeEntrega;
import com.deliveryplatform.merchant.domain.model.Disponibilidade;
import com.deliveryplatform.merchant.domain.model.Documento;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.Faixa;
import com.deliveryplatform.merchant.domain.model.FaixaDeCep;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.domain.model.Identificacao;
import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Operacao;
import com.deliveryplatform.merchant.domain.model.Pausa;
import com.deliveryplatform.merchant.domain.model.PoliticaDeTroco;
import com.deliveryplatform.merchant.domain.model.Telefone;
import com.deliveryplatform.merchant.domain.model.TipoDeOperacao;
import com.deliveryplatform.valuetypes.Money;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A pizzaria da Marli, montada uma vez e usada pelos testes de unidade e de
 * integração.
 *
 * <p>Os números são os do {@code estabelecimento.md} §4 — mínimo de R$ 25,00 na
 * entrega, zero na retirada, fundo de troco de R$ 50,00 — e o horário é o
 * exemplo que o próprio documento usa para a faixa que cruza a meia-noite:
 * <b>terça 18:00–02:00</b>. Uma falha aponta para a linha do documento.
 */
public final class LojaDeTeste {

    private LojaDeTeste() {
    }

    public static Estabelecimento pizzaria() {
        return Estabelecimento.novo(
                identificacao(FusoHorario.PADRAO), operacao(), troco(), disponibilidade(), areas());
    }

    public static Identificacao identificacao(FusoHorario fuso) {
        return new Identificacao(
                "Pizzaria da Marli",
                documento(),
                telefone(),
                "Rua das Palmeiras, 100",
                "Boa Viagem",
                fuso);
    }

    public static Documento documento() {
        return new Documento("12.345.678/0001-95");
    }

    public static Telefone telefone() {
        return Telefone.de("(11) 98765-4321");
    }

    public static Operacao operacao() {
        Map<Modalidade, Set<MetodoPagamento>> metodos = new EnumMap<>(Modalidade.class);
        metodos.put(
                Modalidade.ENTREGA,
                Set.of(MetodoPagamento.DINHEIRO, MetodoPagamento.CARTAO, MetodoPagamento.PIX));
        metodos.put(Modalidade.RETIRADA, Set.of(MetodoPagamento.DINHEIRO, MetodoPagamento.CARTAO));

        Map<Modalidade, Money> minimos = new EnumMap<>(Modalidade.class);
        minimos.put(Modalidade.ENTREGA, Money.de("25.00"));
        minimos.put(Modalidade.RETIRADA, Money.ZERO);

        return new Operacao(TipoDeOperacao.PRODUCAO, metodos, Money.de("5.00"), minimos);
    }

    public static PoliticaDeTroco troco() {
        return new PoliticaDeTroco(Money.de("50.00"), false);
    }

    /**
     * Terça 18:00–02:00 — a faixa que cruza a meia-noite — e sábado com almoço e
     * jantar. Três turnos ao todo, que é o bastante para o teste de integração
     * conferir o reagrupamento sem virar uma tabela de vinte linhas.
     */
    public static Disponibilidade disponibilidade() {
        Map<DayOfWeek, List<Faixa>> horario = new EnumMap<>(DayOfWeek.class);
        horario.put(DayOfWeek.TUESDAY, List.of(Faixa.de("18:00", "02:00")));
        horario.put(
                DayOfWeek.SATURDAY,
                List.of(Faixa.de("11:00", "14:00"), Faixa.de("18:00", "23:00")));
        return new Disponibilidade(horario, Pausa.nenhuma());
    }

    /**
     * Três áreas que cobrem os três casos que importam: ativa com faixa de CEP,
     * ativa sem faixa nenhuma — alcançável só pelo nome, que é o caminho normal
     * da ADR-020 — e desativada com faixa, para provar que M10 e as consultas
     * ignoram quem não está ativa.
     */
    public static List<AreaDeEntrega> areas() {
        return List.of(
                AreaDeEntrega.de(
                        "Boa Viagem",
                        Money.de("7.00"),
                        List.of(FaixaDeCep.de("51000-000", "51999-999"))),
                AreaDeEntrega.de("Centro", Money.ZERO),
                AreaDeEntrega.de(
                                "Pina",
                                Money.de("9.00"),
                                List.of(FaixaDeCep.de("50000-000", "50999-999")))
                        .desativada());
    }

    /** Um instante a partir da hora civil de São Paulo — o fuso padrão da loja. */
    public static Instant emSaoPaulo(String dataHoraLocal) {
        return LocalDateTime.parse(dataHoraLocal)
                .atZone(FusoHorario.PADRAO.zona())
                .toInstant();
    }
}
