package com.deliveryplatform.merchant.application.port.out;

import com.deliveryplatform.merchant.domain.evento.EventoDeDominio;

/**
 * Onde o evento é gravado — <b>não</b> onde ele é publicado.
 *
 * <p>A porta tem um método e ele não fala em fila, broker, exchange ou
 * mensagem. Isso é proposital: quem chama está dentro de uma transação de
 * negócio e o que ela precisa garantir é que o evento vai junto com o fato. Quem
 * entrega, quando entrega e por qual caminho é problema do relay, e um problema
 * que pode mudar — a ADR-043 registra que trocar polling por CDC não mexe em
 * nenhuma linha de domínio, e essa promessa só se sustenta se a porta for assim
 * estreita.
 *
 * <p><b>Não existe {@code publicar}.</b> Se existisse, alguém a chamaria, e a
 * invariante 7 passaria a depender de ninguém errar.
 */
public interface Outbox {

    /**
     * Grava o evento na transação corrente.
     *
     * <p><b>Precondição não verificável por assinatura:</b> deve ser chamado
     * dentro da mesma transação que gravou o fato. Um {@code registrar} fora de
     * transação grava um evento que talvez descreva algo que não aconteceu.
     */
    void registrar(EventoDeDominio evento);
}
