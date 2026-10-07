package com.deliveryplatform.catalog.application.exception;

/**
 * 404 — e <b>a mesma resposta para duas situações diferentes</b>: o produto não
 * existe, ou existe e é de outra loja.
 *
 * <p>É a invariante 9 do {@code CLAUDE.md} em forma de resposta: o identificador que
 * vem da URL nunca é confiado. E é a M7 um nível abaixo — lá, 403 em vez de 404 na
 * loja, para que códigos de status não virem um scanner de lojas; aqui, <b>um 404 só</b>
 * para que não virem um scanner de produtos entre lojas.
 *
 * <p><b>Por que 404 e não 403:</b> quem chama tem vínculo com a loja da URL — a
 * autorização passou. O que falhou foi o produto não estar nela, e do ponto de vista
 * de quem pergunta ele <i>não existe aqui</i>. Um 403 diria "existe, mas não é
 * seu", que é exatamente a frase que um scanner quer ouvir.
 *
 * <p>A mensagem é <b>fixa</b>, pelo mesmo motivo: ela não diz qual das duas
 * situações aconteceu.
 */
public class ProdutoNaoEncontrado extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProdutoNaoEncontrado() {
        super("produto não encontrado nesta loja");
    }
}
