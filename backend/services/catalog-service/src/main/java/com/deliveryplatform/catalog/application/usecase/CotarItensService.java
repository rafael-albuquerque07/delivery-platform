package com.deliveryplatform.catalog.application.usecase;

import com.deliveryplatform.catalog.application.exception.AcessoNegado;
import com.deliveryplatform.catalog.application.exception.ItensIndisponiveis;
import com.deliveryplatform.catalog.application.exception.ItensIndisponiveis.Indisponivel;
import com.deliveryplatform.catalog.application.port.in.CotarItens;
import com.deliveryplatform.catalog.application.port.out.AutorizacaoComercialPort;
import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.exception.OpcoesIndisponiveis;
import com.deliveryplatform.catalog.domain.model.LinhaCotada;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.valuetypes.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Cota os itens de um pedido: valida a seleção e precifica (ADR-018, ADR-088).
 *
 * <p><b>A ordem é a decisão</b> (ADR-088, Decisões 5 a 7):
 *
 * <ol>
 *   <li><b>autoriza pela loja da URL</b> — sem vínculo ativo, 403, e nenhum produto é lido;</li>
 *   <li><b>lê os produtos numa ida só</b>, e cada um a cada cotação: sem cache (ADR-034);</li>
 *   <li>para cada item, na ordem do pedido: produto inexistente, de outra loja ou não vendável
 *       (C6, C10) <b>vai para a lista</b> e o laço segue; produto vendável é cotado pelo agregado,
 *       que confere a estrutura primeiro — o 400 interrompe tudo — e depois junta <b>todas</b> as
 *       opções escolhidas indisponíveis;</li>
 *   <li>lista não vazia, 409 com a lista inteira.</li>
 * </ol>
 *
 * <p>Inexistente e de outra loja caem do mesmo jeito: as duas situações respondem o mesmo (M7).
 */
@Service
public class CotarItensService implements CotarItens {

    private final AutorizacaoComercialPort autorizacao;
    private final ProdutoRepositorio produtos;
    private final Clock relogio;

    public CotarItensService(AutorizacaoComercialPort autorizacao, ProdutoRepositorio produtos,
                             Clock relogio) {
        this.autorizacao = autorizacao;
        this.produtos = produtos;
        this.relogio = relogio;
    }

    @Override
    public Cotacao cotar(UUID estabelecimentoId, List<ItemACotar> itens) {
        // O vínculo ativo basta, sem permissão: a cotação só lê preço de cardápio (ADR-088, Decisão 2).
        autorizacao.contexto(estabelecimentoId).orElseThrow(AcessoNegado::new);

        Map<UUID, Produto> lidos = produtos
                .buscarPorIds(itens.stream().map(ItemACotar::produtoId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Produto::getId, Function.identity()));

        List<LinhaCotada> linhas = new ArrayList<>();
        List<Indisponivel> indisponiveis = new ArrayList<>();
        for (int i = 0; i < itens.size(); i++) {
            ItemACotar item = itens.get(i);
            Produto produto = lidos.get(item.produtoId());
            if (produto == null
                    || !produto.getEstabelecimentoId().equals(estabelecimentoId)
                    || !produto.vendavel()) {
                indisponiveis.add(new Indisponivel(i, item.produtoId(), null));
                continue;
            }
            try {
                linhas.add(produto.cotar(item.quantidade(), item.opcoes()));
            } catch (OpcoesIndisponiveis caidas) {
                for (UUID opcao : caidas.opcoes()) {
                    indisponiveis.add(new Indisponivel(i, item.produtoId(), opcao));
                }
            }
        }

        if (!indisponiveis.isEmpty()) {
            throw new ItensIndisponiveis(indisponiveis);
        }
        Money subtotal = linhas.stream().map(LinhaCotada::subtotal).reduce(Money.ZERO, Money::mais);
        return new Cotacao(linhas, subtotal, Instant.now(relogio));
    }
}
