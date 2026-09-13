package com.deliveryplatform.valuetypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Valor monetário (ADR-009, seção "Tipo monetário").
 *
 * <p>Value object imutável, {@code BigDecimal} de escala 2 <b>e código de
 * moeda</b>, com {@code RoundingMode.HALF_UP} em toda operação. {@code double} e
 * {@code float} são proibidos em toda a cadeia de dinheiro.
 *
 * <p><b>Por que a moeda existe se o sistema é brasileiro.</b> Ela não está aqui
 * para suportar multimoeda — está para que somar reais com outra coisa estoure em
 * vez de somar. É guarda, não recurso, e a ADR-009 a nomeia junto com a escala.
 *
 * <p><b>O nome fica em inglês</b> porque é nome de padrão, não palavra de
 * negócio: a Marli diz troco e bairro, não diz {@code Money} nem
 * {@code Repository} (ADR-035). Os métodos ficam em português porque são leitura
 * de código de domínio, e o resto do domínio deste repositório está em português.
 *
 * <p><b>Sinal negativo é permitido.</b> A ADR-009 exige componentes do pedido
 * {@code ≥ 0}, mas essa é regra do {@code Pedido} e não deste tipo:
 * {@code Ajuste.delta} é {@code Money} e a mesma ADR diz que ele pode ser
 * negativo. Validar sinal aqui tornaria o tipo incapaz de expressar metade do
 * modelo que ele existe para servir.
 */
public record Money(BigDecimal valor, Currency moeda) implements Comparable<Money> {

    /** Escala fixa da ADR-009. O banco guarda {@code precision = 19, scale = 2}. */
    public static final int ESCALA = 2;

    public static final Currency REAL = Currency.getInstance("BRL");

    public static final Money ZERO = de("0");

    public Money {
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(moeda, "moeda");
        valor = valor.setScale(ESCALA, RoundingMode.HALF_UP);
    }

    /**
     * A entrada é texto, não {@code double}. {@code new BigDecimal(0.1)} guarda
     * 0.1000000000000000055511151231257827, e é assim que centavo some.
     */
    public static Money de(String valor) {
        return new Money(new BigDecimal(Objects.requireNonNull(valor, "valor")), REAL);
    }

    public static Money de(BigDecimal valor) {
        return new Money(valor, REAL);
    }

    public Money mais(Money outro) {
        exigirMesmaMoeda(outro);
        return new Money(valor.add(outro.valor), moeda);
    }

    public Money menos(Money outro) {
        exigirMesmaMoeda(outro);
        return new Money(valor.subtract(outro.valor), moeda);
    }

    /**
     * Multiplicação por quantidade inteira — item vezes quantidade. Não há
     * divisão nem percentual: nada no modelo precisa deles hoje, e a ADR-024
     * rejeitou desconto percentual em favor de valor fixo.
     */
    public Money vezes(int quantidade) {
        return new Money(valor.multiply(BigDecimal.valueOf(quantidade)), moeda);
    }

    public boolean ehZero() {
        return valor.signum() == 0;
    }

    public boolean ehNegativo() {
        return valor.signum() < 0;
    }

    public boolean maiorQue(Money outro) {
        return compareTo(outro) > 0;
    }

    public boolean menorQue(Money outro) {
        return compareTo(outro) < 0;
    }

    public boolean maiorOuIgualA(Money outro) {
        return compareTo(outro) >= 0;
    }

    @Override
    public int compareTo(Money outro) {
        exigirMesmaMoeda(outro);
        return valor.compareTo(outro.valor);
    }

    private void exigirMesmaMoeda(Money outro) {
        Objects.requireNonNull(outro, "outro");
        if (!moeda.equals(outro.moeda)) {
            throw new IllegalArgumentException(
                    "moedas diferentes não se combinam: %s e %s"
                            .formatted(moeda.getCurrencyCode(), outro.moeda.getCurrencyCode()));
        }
    }

    /** Formato de log e de mensagem de erro, não de tela. A tela formata com locale. */
    @Override
    public String toString() {
        return "%s %s".formatted(moeda.getCurrencyCode(), valor.toPlainString());
    }
}
