package com.deliveryplatform.catalog.domain.exception;

/**
 * Uma regra do domínio do catálogo foi violada.
 *
 * <p>Uma exceção só para toda a rodada, de propósito: nesta altura o catálogo
 * não tem rota, e quem traduz regra violada em código HTTP é a camada que ainda
 * não existe. Criar agora uma hierarquia de exceções seria desenhar a tradução
 * antes de existir o tradutor.
 *
 * <p><b>Herda de {@code RuntimeException} e mora em {@code domain/exception}</b>,
 * como as exceções de domínio do {@code merchant} e do {@code identity}: o
 * repositório não tem exceção-base de domínio, e cada serviço tem as suas.
 *
 * <p>Os testes afirmam <b>o tipo e um fragmento da mensagem</b>. O fragmento é
 * o que distingue uma regra da outra enquanto houver uma classe só, e é por
 * isso que as mensagens dizem o que foi violado e não "valor inválido" — e,
 * quando a regra é uma invariante do {@code catalogo.md} §9, começam pelo
 * código dela ({@code C1}, {@code C4}), como o {@code merchant} faz com as
 * suas.
 */
public class RegraDoCatalogoViolada extends RuntimeException {

    public RegraDoCatalogoViolada(String mensagem) {
        super(mensagem);
    }
}
