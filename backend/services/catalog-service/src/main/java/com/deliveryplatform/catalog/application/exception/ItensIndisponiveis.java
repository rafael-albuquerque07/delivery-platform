package com.deliveryplatform.catalog.application.exception;

import java.util.List;
import java.util.UUID;

/**
 * O que caiu numa cotação: cada produto que não está à venda, e cada opção escolhida indisponível
 * (ADR-088, Decisão 7). Vira 409 com a lista inteira, para o atendente ver tudo de uma vez.
 */
public class ItensIndisponiveis extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<Indisponivel> indisponiveis;

    public ItensIndisponiveis(List<Indisponivel> indisponiveis) {
        super("itens indisponíveis: " + indisponiveis.size());
        this.indisponiveis = List.copyOf(indisponiveis);
    }

    public List<Indisponivel> indisponiveis() {
        return indisponiveis;
    }

    /**
     * Uma entrada da lista. {@code item} é a posição do item no pedido, a partir de zero;
     * {@code opcaoId} é nulo quando quem caiu foi o produto. <b>Sem nome</b>: o produto de outra
     * loja não pode ter nome na resposta (M7).
     */
    public record Indisponivel(int item, UUID produtoId, UUID opcaoId) {
    }
}
