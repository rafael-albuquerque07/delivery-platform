package com.deliveryplatform.catalog.application.port.in;

import com.deliveryplatform.catalog.domain.model.LinhaCotada;
import com.deliveryplatform.valuetypes.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A cotação dos itens de um pedido, na loja da URL (ADR-018, ADR-088). */
public interface CotarItens {

    Cotacao cotar(UUID estabelecimentoId, List<ItemACotar> itens);

    /** Um item como quem chama o monta: o produto, a quantidade e as opções escolhidas. */
    record ItemACotar(UUID produtoId, int quantidade, List<UUID> opcoes) {

        public ItemACotar {
            opcoes = opcoes == null ? List.of() : List.copyOf(opcoes);
        }
    }

    /** Uma linha por item, na ordem do pedido, e a marca da cotação (ADR-018, emenda de 26/08). */
    record Cotacao(List<LinhaCotada> linhas, Money subtotalDosItens, Instant cotadoEm) {

        public Cotacao {
            linhas = List.copyOf(linhas);
        }
    }
}
