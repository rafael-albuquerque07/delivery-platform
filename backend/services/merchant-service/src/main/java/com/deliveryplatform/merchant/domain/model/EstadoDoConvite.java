package com.deliveryplatform.merchant.domain.model;

/**
 * O ciclo de vida do convite.
 *
 * <p><b>Não há {@code EXPIRADO}, e é a segunda vez nesta dupla de rodadas que
 * um valor de enum do documento não tem produtor.</b> O {@code estabelecimento.md}
 * lista {@code PENDENTE | ACEITO | EXPIRADO | CANCELADO}, mas expirar não é
 * coisa que alguém <i>faz</i> a um convite: é o relógio passando de
 * {@code expiraEm}. Guardar o estado exigiria uma rotina varrendo a tabela para
 * virar a chave — e uma rotina que ninguém escreveu é a peça-que-nunca-rodou;
 * uma que alguém escreve é um segundo lugar que precisa concordar com
 * {@code expiraEm} para sempre.
 *
 * <p>Então "expirado" é <b>derivado</b>: {@code estado == PENDENTE} e
 * {@code agora ≥ expiraEm}. A mesma escolha que fez o
 * {@code identificadorNormalizado} da {@code AreaDeEntrega} ser derivado do
 * nome, e que tirou o {@code CONVIDADO} do {@code Membro} na B1.
 *
 * <p>{@code CANCELADO} fica, porque cancelar <b>é</b> um ato: alguém com
 * {@code GERENCIAR_EQUIPE} decidiu, num instante que dá para registrar.
 */
public enum EstadoDoConvite {

    PENDENTE,
    ACEITO,
    CANCELADO
}
