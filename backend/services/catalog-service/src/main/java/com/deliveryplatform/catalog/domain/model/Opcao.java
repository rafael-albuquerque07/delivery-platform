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
 * <p>O que o acréscimo negativo <b>obriga</b> é a C2: se todo desconto de todo
 * grupo puder ser escolhido ao mesmo tempo, existe uma combinação válida cujo
 * preço unitário não é positivo. Quem cobra isso é
 * {@link Produto#precoMinimoPossivel()}, na publicação.
 *
 * <h2>A disponibilidade da opção tem os mesmos quatro estados do produto</h2>
 *
 * <p>É o que o {@code catalogo.md} §3 manda, na subseção "Disponibilidade da
 * opção": a opção segue as mesmas quatro situações e a mesma reativação. Na
 * rodada G-A este campo era um {@code boolean}, e isso não era uma leitura
 * diferente do documento — era um campo a menos. Um booleano não tem
 * {@code expedienteDeReferencia}, e sem ele a opção que acabou às 23h não volta
 * na abertura seguinte: o comerciante teria de religar cada sabor toda manhã, e
 * o cardápio apodrece.
 *
 * <p>É o caso que mais importa, porque o que acaba quase sempre é a <b>opção</b>
 * e não o produto. A calabresa acaba; "Pizza grande" não.
 *
 * <p><b>Quem marca é o {@link Produto}</b>, por
 * {@link Produto#marcarOpcao(UUID, UUID, Disponibilidade)}. A opção é entidade
 * dentro do agregado e não se altera por fora — é por isso que o único jeito de
 * trocar o estado dela é {@link #com(Disponibilidade)}, que devolve outra opção
 * com o mesmo id.
 *
 * @param acrescimo quanto esta escolha move o preço; pode ser negativo
 * @param ordem     posição dentro do grupo, como o comerciante montou
 */
public record Opcao(
        UUID id,
        String nome,
        Money acrescimo,
        Disponibilidade disponibilidade,
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
        if (disponibilidade == null) {
            throw new RegraDoCatalogoViolada("opção sem disponibilidade");
        }
    }

    /** Opção nova, disponível e sem carimbo — ninguém disse nada sobre ela ainda. */
    public static Opcao nova(String nome, Money acrescimo, int ordem) {
        return new Opcao(UUID.randomUUID(), nome, acrescimo, Disponibilidade.inicial(), ordem);
    }

    /** Mesma opção, outro estado. O id não muda: é a mesma escolha do cardápio. */
    public Opcao com(Disponibilidade nova) {
        return new Opcao(id, nome, acrescimo, nova, ordem);
    }

    /**
     * Derivado, e é o que mantém a terceira cláusula do vendável intacta.
     *
     * <p>O {@link GrupoDeOpcoes#contarDisponiveis()} continua filtrando por
     * {@code Opcao::disponivel} e não mudou uma linha quando o campo deixou de
     * ser booleano. Quem decide se um estado vende continua sendo
     * {@link EstadoDeDisponibilidade#permiteVenda()}, num lugar só.
     */
    public boolean disponivel() {
        return disponibilidade.permiteVenda();
    }
}
