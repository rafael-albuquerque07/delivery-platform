package com.deliveryplatform.catalog.domain.model;

import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.valuetypes.Money;

import java.util.UUID;

/**
 * Uma escolha dentro de um {@link GrupoDeOpcoes}: "queijo extra", "sem cebola",
 * "tamanho grande".
 *
 * <h2>{@code acrescimo} pode ser negativo, e não é engano</h2>
 *
 * <p>"Sem queijo −R$ 2,00" é a mesma coisa que "com bacon +R$ 3,00" do ponto de
 * vista do cardápio: uma opção que move o preço. Modelar o desconto como um
 * segundo conceito duplicaria a soma da cotação e daria duas respostas
 * possíveis para a mesma pergunta.
 *
 * <p>O que <b>não</b> se decide aqui é se o total pode ficar negativo — isso é
 * da cotação, que é a G-C.
 *
 * <h2>{@code disponivel} é booleano aqui, e o documento pede quatro estados</h2>
 *
 * <p>O {@code catalogo.md} §1 e o §4 listam {@code disponivel} na opção sem
 * dizer o tipo. Quem diz é a §3, na subseção "Disponibilidade da opção":
 * <i>"{@code Opcao.disponivel} segue as mesmas quatro situações e a mesma
 * reativação"</i> — e a reativação precisa de {@code expedienteDeReferencia},
 * que um booleano não tem. O documento não se contradiz; <b>este código está
 * atrás dele</b>.
 *
 * <p>Fica booleano até a G-C, quando a reativação ganha código e a troca é
 * testada junto com ela: a opção passa a ter {@link Disponibilidade}, e o
 * {@link GrupoDeOpcoes#contarDisponiveis()} passa a perguntar
 * {@code op.disponibilidade().permiteVenda()}. A fórmula do vendável não muda.
 *
 * @param acrescimo quanto esta escolha move o preço; pode ser negativo
 * @param ordem     posição dentro do grupo, como o comerciante montou
 */
public record Opcao(
        UUID id,
        String nome,
        Money acrescimo,
        boolean disponivel,
        int ordem
) {

    public Opcao {
        if (id == null) {
            throw new RegraDoCatalogoViolada("opção sem id");
        }
        nome = Textos.exigirPreenchido(nome, "nome da opção");
        if (acrescimo == null) {
            throw new RegraDoCatalogoViolada("opção sem acréscimo — use zero, não nulo");
        }
    }

    /** Opção nova, disponível, na posição pedida. */
    public static Opcao nova(String nome, Money acrescimo, int ordem) {
        return new Opcao(UUID.randomUUID(), nome, acrescimo, true, ordem);
    }

    public Opcao comDisponibilidade(boolean disponivel) {
        return new Opcao(id, nome, acrescimo, disponivel, ordem);
    }
}
