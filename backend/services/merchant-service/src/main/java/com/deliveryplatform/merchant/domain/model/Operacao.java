package com.deliveryplatform.merchant.domain.model;

import com.deliveryplatform.valuetypes.Money;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * O que a loja aceita e por quanto (`estabelecimento.md` §4).
 *
 * <p><b>{@code modalidadesAceitas} não é campo.</b> Era, no documento, ao lado de
 * {@code metodosPorModalidade} — e as chaves de um eram os elementos do outro.
 * Dois campos que precisam ser iguais são uma invariante a testar para sempre e a
 * violar por descuido, que é exatamente o argumento com que a emenda de 26/08
 * colapsou {@code deliveryFee} em {@code taxaSnapshot} na ADR-009. A loja aceita
 * a modalidade se há entrada para ela no mapa, e {@link #modalidadesAceitas()} é
 * derivado — não guardado.
 *
 * <p><b>Métodos são matriz, não lista</b> (`estabelecimento.md` §4): aceitar Pix
 * no balcão e recusar Pix na entrega é configuração comum, e uma lista única não
 * a expressa.
 *
 * <p><b>O mínimo é total sobre as modalidades aceitas.</b> Toda modalidade aceita
 * tem entrada, e nenhuma modalidade não aceita tem. Zero é valor válido e
 * significa "sem mínimo"; a <i>ausência</i> seria confundida com zero, e é
 * precisamente o erro que M11 já nomeia do outro lado — <i>"ausência de área ≠
 * taxa zero"</i>. Entrada para modalidade que a loja não aceita é o simétrico:
 * configuração morta que vira ativa no dia em que alguém aceitar a modalidade.
 */
public record Operacao(
        TipoDeOperacao tipoDeOperacao,
        Map<Modalidade, Set<MetodoPagamento>> metodosPorModalidade,
        Money descontoDeRetirada,
        Map<Modalidade, Money> pedidoMinimoPorModalidade) {

    public Operacao {
        Objects.requireNonNull(tipoDeOperacao, "tipoDeOperacao");
        Objects.requireNonNull(metodosPorModalidade, "metodosPorModalidade");
        Objects.requireNonNull(descontoDeRetirada, "descontoDeRetirada");
        Objects.requireNonNull(pedidoMinimoPorModalidade, "pedidoMinimoPorModalidade");

        // M12: loja que não entrega nem deixa retirar não opera. E modalidade
        // aceita sem forma de pagar é pedido que entra e não fecha.
        if (metodosPorModalidade.isEmpty()) {
            throw new IllegalArgumentException(
                    "a loja precisa aceitar ao menos uma modalidade (M12)");
        }
        metodosPorModalidade.forEach((modalidade, metodos) -> {
            Objects.requireNonNull(metodos, "métodos de " + modalidade);
            if (metodos.isEmpty()) {
                throw new IllegalArgumentException(
                        "modalidade sem método de pagamento aceito: " + modalidade + " (M12)");
            }
        });

        // M15
        if (descontoDeRetirada.ehNegativo()) {
            throw new IllegalArgumentException(
                    "descontoDeRetirada não pode ser negativo: " + descontoDeRetirada);
        }

        // M17
        if (!pedidoMinimoPorModalidade.keySet().equals(metodosPorModalidade.keySet())) {
            throw new IllegalArgumentException(
                    "pedidoMinimoPorModalidade precisa ter entrada para toda modalidade aceita "
                            + "e para nenhuma outra — aceitas %s, mínimos %s (M17)"
                                    .formatted(metodosPorModalidade.keySet(),
                                            pedidoMinimoPorModalidade.keySet()));
        }
        pedidoMinimoPorModalidade.forEach((modalidade, minimo) -> {
            Objects.requireNonNull(minimo, "pedido mínimo de " + modalidade);
            if (minimo.ehNegativo()) {
                throw new IllegalArgumentException(
                        "pedido mínimo negativo em " + modalidade + ": " + minimo + " (M17)");
            }
        });

        metodosPorModalidade = copiaDosMetodos(metodosPorModalidade);
        pedidoMinimoPorModalidade = Map.copyOf(pedidoMinimoPorModalidade);
    }

    /** Derivado, não campo. É {@code metodosPorModalidade.keySet()}, e essa é a decisão. */
    public Set<Modalidade> modalidadesAceitas() {
        return metodosPorModalidade.keySet();
    }

    public boolean aceita(Modalidade modalidade) {
        return metodosPorModalidade.containsKey(modalidade);
    }

    /** Vazio quando a modalidade não é aceita — nunca nulo. */
    public Set<MetodoPagamento> metodosDe(Modalidade modalidade) {
        return metodosPorModalidade.getOrDefault(modalidade, Set.of());
    }

    /**
     * O mínimo da modalidade. Só faz sentido perguntar por modalidade aceita, e
     * a invariante acima garante que toda aceita tem resposta.
     */
    public Money pedidoMinimoDe(Modalidade modalidade) {
        Money minimo = pedidoMinimoPorModalidade.get(modalidade);
        if (minimo == null) {
            throw new IllegalArgumentException("modalidade não aceita por esta loja: " + modalidade);
        }
        return minimo;
    }

    /**
     * Cópia defensiva em dois níveis. {@code Map.copyOf} sozinho congelaria o
     * mapa e deixaria os conjuntos de dentro mutáveis — quem passou o mapa
     * continuaria podendo acrescentar um método de pagamento depois da
     * validação.
     */
    private static Map<Modalidade, Set<MetodoPagamento>> copiaDosMetodos(
            Map<Modalidade, Set<MetodoPagamento>> origem) {
        Map<Modalidade, Set<MetodoPagamento>> copia = new EnumMap<>(Modalidade.class);
        origem.forEach((modalidade, metodos) -> copia.put(modalidade, Set.copyOf(metodos)));
        return Map.copyOf(copia);
    }
}
