package com.deliveryplatform.catalog.api.dto;

import com.deliveryplatform.catalog.application.port.in.CotarItens.Cotacao;
import com.deliveryplatform.catalog.domain.model.LinhaCotada;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.OpcaoCotada;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A cotação: uma linha por item do pedido, na ordem do pedido, e o subtotal dos itens. Dinheiro
 * sai como número, como o {@code precoBase} das outras respostas do {@code catalog}.
 */
public record CotacaoResponse(List<Linha> linhas, BigDecimal subtotalDosItens, Instant cotadoEm) {

    public static CotacaoResponse de(Cotacao cotacao) {
        return new CotacaoResponse(
                cotacao.linhas().stream().map(Linha::de).toList(),
                cotacao.subtotalDosItens().valor(),
                cotacao.cotadoEm());
    }

    public record Linha(
            UUID produtoId,
            String nomeDoProduto,
            BigDecimal precoBase,
            List<Opcao> opcoes,
            BigDecimal precoUnitario,
            int quantidade,
            BigDecimal subtotal,
            ModoDeControle modoDeControle) {

        static Linha de(LinhaCotada l) {
            return new Linha(l.produtoId(), l.nomeDoProduto(), l.precoBase().valor(),
                    l.opcoes().stream().map(Opcao::de).toList(),
                    l.precoUnitario().valor(), l.quantidade(), l.subtotal().valor(),
                    l.modoDeControle());
        }
    }

    public record Opcao(UUID opcaoId, String nomeDoGrupo, String nomeDaOpcao, BigDecimal acrescimo) {

        static Opcao de(OpcaoCotada o) {
            return new Opcao(o.opcaoId(), o.nomeDoGrupo(), o.nomeDaOpcao(), o.acrescimo().valor());
        }
    }
}
