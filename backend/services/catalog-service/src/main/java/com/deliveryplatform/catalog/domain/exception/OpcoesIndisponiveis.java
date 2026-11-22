package com.deliveryplatform.catalog.domain.exception;

import java.util.List;
import java.util.UUID;

/**
 * As opções escolhidas que estão indisponíveis agora — <b>todas</b>, não só a primeira (ADR-088,
 * Decisão 7). É o mundo que mudou, não erro de quem chamou: o {@code catalogo.md} §5 dá 409.
 *
 * <p>Não sai do caso de uso. O {@code CotarItensService} a captura por item e junta as opções na
 * lista dos indisponíveis; o {@code TratadorDeErros} não a mapeia, e se ela chegar lá é defeito.
 */
public class OpcoesIndisponiveis extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<UUID> opcoes;

    public OpcoesIndisponiveis(List<UUID> opcoes) {
        super("opções indisponíveis: " + opcoes.size());
        this.opcoes = List.copyOf(opcoes);
    }

    /** Na ordem dos grupos e, dentro do grupo, na ordem das opções. */
    public List<UUID> opcoes() {
        return opcoes;
    }
}
