package com.deliveryplatform.catalog.domain.model;

/**
 * O que o dia fez com este produto.
 *
 * <p>São os quatro da tabela do {@code catalogo.md} §3, com os nomes de lá, e a
 * diferença entre os dois últimos é a única coisa que importa aqui:
 * <b>{@code ESGOTADO_HOJE} volta sozinho; {@code ESGOTADO_INDETERMINADO} não
 * volta nunca sem alguém dizer.</b>
 *
 * <ul>
 *   <li>{@code DISPONIVEL} — tem.</li>
 *   <li>{@code ACABANDO} — tem, e o comerciante quis avisar. <b>Vende.</b> É
 *       sinal para o consumidor, não trava.</li>
 *   <li>{@code ESGOTADO_HOJE} — acabou <i>neste expediente</i>. Reativa na
 *       abertura do próximo ({@code catalogo.md} §3), por comparação de
 *       {@code expedienteDeReferencia}.</li>
 *   <li>{@code ESGOTADO_INDETERMINADO} — acabou sem data para voltar. Só volta
 *       por ato do comerciante.</li>
 * </ul>
 *
 * @see Produto#vendavel()
 */
public enum EstadoDeDisponibilidade {

    DISPONIVEL,
    ACABANDO,
    ESGOTADO_HOJE,
    ESGOTADO_INDETERMINADO;

    /**
     * A segunda cláusula do vendável da §5: {@code disponibilidade ∈
     * {DISPONIVEL, ACABANDO}}.
     *
     * <p>Mora aqui, e não no {@link Produto}, para que exista <b>um</b> lugar
     * no repositório que decide se um estado vende. Quando o quinto estado
     * aparecer, é este método que muda.
     *
     * <p>Note que a pergunta é feita por inclusão e não por exclusão: escrever
     * {@code != ESGOTADO_HOJE && != ESGOTADO_INDETERMINADO} faria o estado novo vender
     * por omissão, que é o pior jeito de errar.
     */
    public boolean permiteVenda() {
        return this == DISPONIVEL || this == ACABANDO;
    }
}
