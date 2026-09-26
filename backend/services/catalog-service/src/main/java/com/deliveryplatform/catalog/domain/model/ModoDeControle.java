package com.deliveryplatform.catalog.domain.model;

/**
 * Como este produto controla o que tem para vender.
 *
 * <ul>
 *   <li>{@code SEM_CONTROLE} — não acaba. Refrigerante em lata, couvert, taxa.
 *       A disponibilidade dele é sempre {@code DISPONIVEL}, e o agregado recusa
 *       qualquer outra coisa (ver {@link Produto}).</li>
 *   <li>{@code QUALITATIVO} — o comerciante diz "acabando" ou "acabou hoje", e
 *       o produto volta sozinho na abertura do próximo expediente. É a premissa
 *       P6 do produto: <i>disponibilidade qualitativa</i>. Ninguém conta
 *       unidade.</li>
 * </ul>
 *
 * <h2>Por que não existe {@code QUANTITATIVO} aqui</h2>
 *
 * <p>O {@code catalogo.md} §1 lista três valores. O terceiro,
 * {@code QUANTITATIVO}, é controle por quantidade — <b>e ele não tem produtor,
 * não tem tela, não tem baixa de estoque e não tem uso até o marco 10</b>. Este
 * repositório já recusou três vezes o valor de enum sem emissor: na B1, na B2 e
 * na F (o {@code MotivoDoExpediente}, que nasceu com <b>um</b> valor porque só
 * um tinha produtor). Valor sem uso é promessa com sintaxe de código: ele passa
 * pelo compilador, aparece no OpenAPI, entra no dropdown de quem for desenhar a
 * tela, e não faz nada.
 *
 * <p><b>Nascer com dois valores é a regra mais forte, não a mais fraca.</b> Com
 * três valores, "{@code QUANTITATIVO} é inválido na publicação até o marco 10"
 * seria uma regra em tempo de execução, com teste, que alguém pode remover sem
 * perceber. Com dois, é o sistema de tipos: não existe valor para atribuir.
 *
 * <p><b>Gatilho escrito.</b> {@code QUANTITATIVO} nasce no marco 10, junto com
 * a baixa de estoque que o torna verdadeiro — e não antes. Acrescentar valor a
 * enum é mudança compatível (ADR-027), então o custo de esperar é zero e o
 * custo de antecipar é um campo que mente.
 */
public enum ModoDeControle {

    SEM_CONTROLE,
    QUALITATIVO
}
