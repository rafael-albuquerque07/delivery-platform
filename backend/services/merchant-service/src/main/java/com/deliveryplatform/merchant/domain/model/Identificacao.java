package com.deliveryplatform.merchant.domain.model;

import java.util.Objects;

/**
 * Quem a loja é e onde ela está (`estabelecimento.md` §1).
 *
 * <p><b>O endereço é texto, não estrutura.</b> Nenhuma invariante deste serviço
 * lê pedaço de endereço: as faixas de CEP da ADR-020 casam com o endereço do
 * <i>cliente</i>, não com o da loja, e vivem na {@code AreaDeEntrega}. E a
 * ADR-013 já escolheu esta representação para o sistema inteiro — <i>"endereço
 * textual e nome de bairro"</i> no lugar de coordenada. Um endereço em sete
 * campos custaria sete linhas numa tela que a H1.1 quer curta, mais validação de
 * UF e CEP a manter, para responder pergunta que ninguém faz.
 *
 * <p>O {@code bairro} fica separado porque é a unidade do modelo de entrega
 * (ADR-020): é a palavra que a Marli usa para dizer onde entrega, e o bairro da
 * própria loja é o ponto de partida natural do cadastro das áreas.
 */
public record Identificacao(
        String nome,
        Documento documento,
        Telefone telefone,
        String enderecoTextual,
        String bairro,
        FusoHorario fusoHorario) {

    public Identificacao {
        nome = exigirTexto(nome, "nome");
        Objects.requireNonNull(documento, "documento");
        Objects.requireNonNull(telefone, "telefone");
        enderecoTextual = exigirTexto(enderecoTextual, "enderecoTextual");
        bairro = exigirTexto(bairro, "bairro");
        // M16: nunca nulo. O tipo já recusa zona fora do conjunto brasileiro.
        Objects.requireNonNull(fusoHorario, "fusoHorario");
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório e não pode ser vazio");
        }
        return valor.trim();
    }
}
