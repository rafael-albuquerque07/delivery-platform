package com.deliveryplatform.merchant.api.controller;

import com.deliveryplatform.merchant.api.seguranca.SujeitoDoToken;
import com.deliveryplatform.merchant.application.port.in.ConsultarContextoDeAcesso;
import com.deliveryplatform.merchant.application.port.in.ContextoDeAcesso;
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
 * A primeira rota interna do repositório — a que faz a ADR-045 deixar de ser
 * texto.
 *
 * <h2>O caminho, e cada pedaço dele</h2>
 *
 * <pre>
 * GET /internal/merchants/{estabelecimentoId}/me/contexto-de-acesso
 * </pre>
 *
 * <p><b>{@code /internal/}</b> porque o gateway não roteia esse prefixo — as
 * catorze rotas dele são todas {@code /api/v1/**} (ADR-045). O único caminho
 * interno que algum documento já fixava é o {@code POST /internal/catalog/quote}
 * da ADR-018; este é o segundo, e a emenda desta rodada o registra.
 *
 * <p><b>{@code {estabelecimentoId}}, e não {@code {merchantId}}.</b> A ADR-035 e
 * o {@code CLAUDE.md} mandam identificador e recurso em português, e o
 * {@link EquipeController} já usa esse nome. O {@code merchantId} existe só
 * como variável de predicado no gateway, que não vê esta rota.
 *
 * <p><b>{@code /me/}</b> porque o gateway já usa {@code me} em duas rotas —
 * {@code /api/v1/me/**} e {@code /api/v1/couriers/me/**} — com exatamente este
 * sentido: o portador do token. E ele fecha uma porta: com {@code me} no
 * caminho, ninguém acrescenta depois um {@code /{usuarioId}/contexto-de-acesso}
 * sem perceber que está criando outro recurso. É a porta que a ADR-045 quer
 * fechada — <i>"responde sobre o portador do token, nunca sobre um
 * {@code usuarioId} no caminho"</i>.
 *
 * <h2>Nenhuma linha nova de segurança</h2>
 *
 * <p>O {@code SecurityConfig} deste serviço tem um matcher só —
 * {@code /actuator/health/**} liberado — e {@code anyRequest().authenticated()}.
 * Esta rota exige token válido pela regra que já existe, e passa pelos mesmos
 * validadores de emissor e audiência. Não há credencial de serviço neste sistema
 * (ADR-045), então não há nada a distinguir: uma linha para {@code /internal/**}
 * só faria sentido se ele pedisse algo diferente de um token válido.
 */
@RestController
@RequestMapping("/internal/merchants/{estabelecimentoId}/me")
@Tag(name = "interno",
        description = "Consumido por outro serviço, com o token de quem pediu encaminhado. "
                + "Não roteado pelo gateway — ADR-045.")
public class ContextoDeAcessoController {

    private final ConsultarContextoDeAcesso contextos;

    public ContextoDeAcessoController(ConsultarContextoDeAcesso contextos) {
        this.contextos = contextos;
    }

    /**
     * O contexto de acesso do portador do token naquela loja.
     *
     * <p><b>403 para as quatro recusas</b>, com o mesmo corpo: loja
     * inexistente, sem vínculo, vínculo suspenso e vínculo removido. O
     * {@code TratadorDeErros} já faz essa tradução, e M7 é o motivo — respostas
     * diferentes transformariam a rota num scanner de estabelecimentos.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O contexto do portador nesta loja"),
            @ApiResponse(responseCode = "400", description = "Identificador da loja malformado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo ativo com a loja, ou loja inexistente — a mesma recusa", content = @Content)
    })
    @GetMapping(path = "/contexto-de-acesso", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "O contexto de acesso do portador do token nesta loja",
            description = "Exige apenas token válido — nenhuma permissão. Responde sobre o "
                    + "portador e sobre mais ninguém: não existe usuarioId na entrada. "
                    + "Sem vínculo ativo e loja inexistente devolvem a mesma recusa.")
    public ContextoDeAcesso doPortador(
            @PathVariable UUID estabelecimentoId, @AuthenticationPrincipal Jwt token) {

        return contextos.doPortador(estabelecimentoId, SujeitoDoToken.de(token));
    }
}
