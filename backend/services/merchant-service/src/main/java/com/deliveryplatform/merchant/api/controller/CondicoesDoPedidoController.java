package com.deliveryplatform.merchant.api.controller;

import com.deliveryplatform.merchant.api.dto.CondicoesDoPedidoRequest;
import com.deliveryplatform.merchant.api.dto.CondicoesDoPedidoResponse;
import com.deliveryplatform.merchant.api.seguranca.SujeitoDoToken;
import com.deliveryplatform.merchant.application.port.in.ConsultarCondicoesDoPedido;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * As condições do pedido — o que a T01 congela, vindo do {@code merchant} (ADR-087).
 *
 * <pre>
 * POST /internal/merchants/{estabelecimentoId}/condicoes-do-pedido
 * </pre>
 *
 * <p><b>{@code POST}</b>, para que o bairro e o CEP fiquem no corpo, e não na URL nem em log de
 * acesso. É uma consulta: não grava nada. <b>Sem cache</b> em quem chama — a resposta é congelada
 * no pedido (ADR-034).
 *
 * <p>Endereço fora de toda área ativa é <b>200</b>, com {@code atendida} falso, sem taxa (M11), e
 * a lista dos bairros atendidos (ADR-020): é uma resposta, não um erro de quem chamou. Autorização
 * e códigos, os do {@link ExpedienteCorrenteController}.
 */
@RestController
@RequestMapping("/internal/merchants/{estabelecimentoId}")
@Tag(name = "interno",
        description = "Consumido por outro serviço, com o token de quem pediu encaminhado. "
                + "Não roteado pelo gateway — ADR-045.")
public class CondicoesDoPedidoController {

    private final ConsultarCondicoesDoPedido condicoes;

    public CondicoesDoPedidoController(ConsultarCondicoesDoPedido condicoes) {
        this.condicoes = condicoes;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "As condições da modalidade, e a área quando for entrega"),
            @ApiResponse(responseCode = "400", description = "Identificador malformado, corpo inválido, entrega sem bairro e sem CEP, ou CEP inválido", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo ativo com a loja, ou loja inexistente — a mesma recusa", content = @Content)
    })
    @PostMapping(path = "/condicoes-do-pedido",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "O que a T01 congela: métodos, pedido mínimo, desconto, troco e a área com a taxa",
            description = "Na entrega, o bairro manda e o CEP é a alternativa (ADR-020). Fora de toda "
                    + "área ativa: 200, atendida falso, sem taxa, com os bairros atendidos. "
                    + "Exige vínculo ativo, e nenhuma permissão específica (ADR-087).")
    public CondicoesDoPedidoResponse condicoesDoPedido(
            @PathVariable UUID estabelecimentoId,
            @Valid @RequestBody CondicoesDoPedidoRequest corpo,
            @AuthenticationPrincipal Jwt token) {
        return CondicoesDoPedidoResponse.de(condicoes.de(
                estabelecimentoId, SujeitoDoToken.de(token),
                corpo.modalidade(), corpo.bairro(), corpo.cep()));
    }
}
