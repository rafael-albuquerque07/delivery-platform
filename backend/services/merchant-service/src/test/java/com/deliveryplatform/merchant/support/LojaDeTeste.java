package com.deliveryplatform.merchant.support;

import com.deliveryplatform.merchant.domain.model.Documento;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.FusoHorario;
import com.deliveryplatform.merchant.domain.model.Identificacao;
import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Operacao;
import com.deliveryplatform.merchant.domain.model.PoliticaDeTroco;
import com.deliveryplatform.merchant.domain.model.Telefone;
import com.deliveryplatform.merchant.domain.model.TipoDeOperacao;
import com.deliveryplatform.valuetypes.Money;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * A pizzaria da Marli, montada uma vez e usada pelo teste de unidade e pelo de
 * integração. Fica em {@code support} pelo mesmo motivo do
 * {@code GeradorDeChaveDeTeste} do {@code identity-service}: fixture
 * compartilhada entre pacotes de teste não é teste, e um teste importando outro
 * amarra a ordem de leitura de quem vier depois.
 *
 * <p>Os números são os do {@code estabelecimento.md} §4 — mínimo de R$ 25,00 na
 * entrega, zero na retirada, fundo de troco de R$ 50,00 — para que uma falha
 * aponte para a linha do documento.
 */
public final class LojaDeTeste {

    private LojaDeTeste() {
    }

    public static Estabelecimento pizzaria() {
        return Estabelecimento.novo(identificacao(FusoHorario.PADRAO), operacao(), troco());
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
}
