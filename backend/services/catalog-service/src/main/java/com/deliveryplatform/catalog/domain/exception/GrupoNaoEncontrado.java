package com.deliveryplatform.catalog.domain.exception;

/**
 * 404 — o grupo que a URL nomeia não é deste produto (ADR-077).
 *
 * <p>É a {@link OpcaoNaoEncontrada} sem o último nível: a loja e o produto já foram
 * conferidos, e o que falta é o grupo onde a opção nova entraria. Mora em
 * {@code domain/exception} pelo mesmo motivo dela: quem procura o grupo é o
 * {@code Produto.criarOpcao}, dentro da raiz.
 *
 * <p>Não reaproveita a {@link OpcaoNaoEncontrada} porque ali não há opção nenhuma a
 * procurar — a pessoa está criando uma —, e uma frase que dissesse "esta opção não
 * existe mais" a quem acabou de digitá-la descreveria outra situação.
 *
 * <p>A mensagem é <b>fixa</b>.
 */
public class GrupoNaoEncontrado extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GrupoNaoEncontrado() {
        super("grupo não encontrado neste produto");
    }
}
