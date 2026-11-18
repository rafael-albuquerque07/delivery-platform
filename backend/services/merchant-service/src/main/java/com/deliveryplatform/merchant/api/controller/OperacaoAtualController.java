package com.deliveryplatform.merchant.api.controller;

import com.deliveryplatform.merchant.api.dto.OperacaoAtualResponse;
import com.deliveryplatform.merchant.api.seguranca.SujeitoDoToken;
import com.deliveryplatform.merchant.application.port.in.ConsultarOperacaoAtual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * A operação da loja — o que a {@code OperacaoDoEstabelecimentoPort} do {@code estabelecimento.md}
 * §3 declara, do lado do {@code merchant} (ADR-087).
 *
 * <pre>
 * GET /internal/merchants/{estabelecimentoId}/operacao
 * </pre>
 *
 * <p>Na forma do {@link ExpedienteCorrenteController}: {@code /internal/}, o token de quem pediu
 * (ADR-045), vínculo ativo e nenhuma permissão, e 403 também para loja inexistente. É a resposta
 * que o {@code order} pode guardar em cache curto, porque é usada e descartada — ao contrário das
 * condições do pedido.
 */
@RestController
@RequestMapping("/internal/merchants/{estabelecimentoId}")
@Tag(name = "interno",
        description = "Consumido por outro serviço, com o token de quem pediu encaminhado. "
                + "Não roteado pelo gateway — ADR-045.")
public class OperacaoAtualController {

    private final ConsultarOperacaoAtual operacoes;

    public OperacaoAtualController(ConsultarOperacaoAtual operacoes) {
        this.operacoes = operacoes;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se a loja está aberta agora, e o tipo de operação"),
            @ApiResponse(responseCode = "400", description = "Identificador da loja malformado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo ativo com a loja, ou loja inexistente — a mesma recusa", content = @Content)
    })
    @GetMapping(path = "/operacao", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Se a loja está aberta agora, e como ela opera",
            description = "aberto compõe horário e pausa. Exige vínculo ativo, e nenhuma permissão "
                    + "específica (ADR-087).")
    public OperacaoAtualResponse operacao(
            @PathVariable UUID estabelecimentoId, @AuthenticationPrincipal Jwt token) {
        return OperacaoAtualResponse.de(operacoes.de(estabelecimentoId, SujeitoDoToken.de(token)));
    }
}
