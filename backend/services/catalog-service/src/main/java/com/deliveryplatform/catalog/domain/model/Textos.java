package com.deliveryplatform.catalog.domain.model;

import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
/**
 * As duas linhas de validação de texto que quatro classes desta rodada repetiam.
 *
 * <p>Existe para que "nome em branco" tenha <b>uma</b> definição no catálogo.
 * O {@code null}, a string vazia e três espaços são a mesma coisa para quem
 * olha o cardápio, e um deles escapar em um dos quatro lugares é o tipo de
 * defeito que só aparece na tela do consumidor.
 *
 * <p><b>Não há limite de tamanho aqui, e é de propósito.</b> O
 * {@code catalogo.md} não escreveu nenhum, e inventar 120 caracteres agora
 * significaria descobrir o número certo no dia em que um comerciante não
 * conseguisse cadastrar o nome do prato dele. Quando o limite existir, ele
 * nasce no documento e vem para cá.
 */
final class Textos {

    private Textos() {
    }

    static String exigirPreenchido(String valor, String oQue) {
        if (valor == null || valor.isBlank()) {
            throw new RegraDoCatalogoViolada(oQue + " é obrigatório");
        }
        return valor.trim();
    }

    /** Texto opcional: em branco e ausente são a mesma coisa, e viram {@code null}. */
    static String normalizarOpcional(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
