package com.deliveryplatform.catalog.api.controller;

import com.deliveryplatform.catalog.api.dto.CotacaoRequest;
import com.deliveryplatform.catalog.api.dto.CotacaoResponse;
import com.deliveryplatform.catalog.application.port.in.CotarItens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A cotação do catálogo (ADR-018, ADR-088):
 *
 * <pre>
 * POST /internal/merchants/{estabelecimentoId}/catalog/quote
 * </pre>
 *
 * <p>A primeira rota interna do {@code catalog}, na forma das do {@code merchant}: {@code /internal/},
 * o token de quem pediu (ADR-045), a loja na URL, e vínculo ativo sem permissão. {@code POST} porque
 * a seleção vai no corpo; a rota não grava nada e não reserva nada (§5 do {@code catalogo.md}).
 */
@RestController
@RequestMapping("/internal/merchants/{estabelecimentoId}/catalog")
@Tag(name = "interno",
        description = "Consumido por outro serviço, com o token de quem pediu encaminhado. "
                + "Não roteado pelo gateway — ADR-045.")
public class CotacaoController {

    private final CotarItens cotacoes;

    public CotacaoController(CotarItens cotacoes) {
        this.cotacoes = cotacoes;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uma linha por item, com os nomes e os preços que a T01 congela"),
            @ApiResponse(responseCode = "400", description = "Corpo malformado, identificador malformado, opção que não é do produto, opção repetida, ou escolhas fora do mínimo ou do máximo de um grupo — o defeito de quem chama vence o 409 (ADR-088)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo ativo com a loja, loja inexistente ou merchant indisponível — a mesma recusa", content = @Content),
            @ApiResponse(responseCode = "409", description = "Há itens que não estão à venda agora — produto fora do ar, não vendável, de outra loja ou inexistente, ou opção indisponível; a lista inteira vem na propriedade indisponiveis (ADR-088)", content = @Content)
    })
    @PostMapping(path = "/quote", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cota os itens de um pedido: valida a seleção e precifica",
            description = "Exige vínculo ativo, e nenhuma permissão específica (ADR-088). Sem "
                    + "cache: cada chamada lê os produtos do banco.")
    public CotacaoResponse cotar(
            @PathVariable UUID estabelecimentoId,
            @Valid @RequestBody CotacaoRequest pedido) {

        return CotacaoResponse.de(cotacoes.cotar(estabelecimentoId, pedido.paraComando()));
    }
}
