package com.deliveryplatform.catalog.domain.model;

/**
 * Onde o produto está na vida dele — decisão do comerciante, não do dia.
 *
 * <p>Os três estados respondem perguntas diferentes, e é por isso que são três
 * e não um booleano {@code publicado}:
 *
 * <ul>
 *   <li>{@code RASCUNHO} — <b>nunca apareceu</b> no cardápio. O comerciante
 *       está montando; faltam foto, preço, grupos. Nada aqui precisa estar
 *       completo.</li>
 *   <li>{@code ATIVO} — aparece no cardápio público. Para chegar aqui o produto
 *       tem de ser <b>estruturalmente vendável</b> (ver
 *       {@link Produto#publicar()}).</li>
 *   <li>{@code INATIVO} — <b>já apareceu e foi retirado</b>. O sanduíche de
 *       inverno em janeiro. Volta para {@code ATIVO} sem ser remontado.</li>
 * </ul>
 *
 * <p><b>A diferença entre {@code RASCUNHO} e {@code INATIVO} não é cosmética.</b>
 * Um rascunho nunca foi ao ar, então retirá-lo não significa nada — e por isso
 * {@link Produto#inativar()} recusa essa transição. Se os dois fossem o mesmo
 * "não aparece", o comerciante perderia a lista do que ele está montando dentro
 * da lista do que ele tirou do ar, que costuma ser dez vezes maior.
 *
 * <p><b>Publicação não é disponibilidade.</b> Este enum diz o que o comerciante
 * decidiu; {@link EstadoDeDisponibilidade} diz o que o dia fez. Um produto
 * {@code ATIVO} e {@code ESGOTADO_HOJE} continua {@code ATIVO} — ele volta
 * sozinho na abertura do próximo expediente, e nada no cadastro foi mexido.
 */
public enum EstadoDePublicacao {

    RASCUNHO,
    ATIVO,
    INATIVO
}
