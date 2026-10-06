package com.deliveryplatform.merchant.api.controller;

import com.deliveryplatform.merchant.api.dto.ExpedienteCorrenteResponse;
import com.deliveryplatform.merchant.api.seguranca.SujeitoDoToken;
import com.deliveryplatform.merchant.application.port.in.ConsultarExpedienteCorrente;
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
 * A segunda rota {@code /internal/} do {@code merchant}, e a que a ADR-046 §6
 * prometeu há quatro dias.
 *
 * <pre>
 * GET /internal/merchants/{estabelecimentoId}/expediente-corrente
 * </pre>
 *
 * <h2>Ela existe porque alguém vai chamá-la</h2>
 *
 * <p>A G-C2 escreve a marcação de disponibilidade no {@code catalog}, e
 * {@code ESGOTADO_HOJE} não nasce sem expediente — o construtor da
 * {@code Disponibilidade} de lá recusa o carimbo pela metade. O catálogo não
 * pode calcular o dia operacional, e esta é a rota por onde ele o recebe.
 *
 * <p>A G-B2 deixou esta rota para quando ela tivesse chamador. O chamador é a
 * rodada seguinte — a mesma distância que a G-B5 teve do front.
 *
 * <h2>{@code /internal/}, e a credencial é o token de quem pediu</h2>
 *
 * <p>O gateway não roteia {@code /internal/} (ADR-012), e a ADR-045 decidiu que
 * não existe credencial entre serviços: o {@code catalog} encaminha o token do
 * comerciante que apertou "acabou". Há gente do outro lado, e por isso esta
 * rota cabe na ADR-045 sem tocar no que ela deixou em aberto.
 *
 * <h2>Vínculo ativo, e nenhuma permissão</h2>
 *
 * <p>A invariante 9 manda confrontar o identificador da URL com o usuário
 * autenticado, e o confronto aqui é o vínculo. <b>Permissão específica seria
 * duplicação</b>: quem chama já vai conferir a permissão do ato que vai
 * praticar — {@code ALTERAR_PRODUTO}, na G-C2 —, e exigir uma segunda aqui
 * significaria manter duas listas em concordância para sempre.
 *
 * <h2>Os três códigos</h2>
 *
 * <table>
 *   <tr><td>{@code 200}</td><td>a data a carimbar</td></tr>
 *   <tr><td>{@code 403}</td><td>sem vínculo ativo — <b>também</b> quando a loja
 *       não existe, para que a diferença de código não enumere lojas</td></tr>
 *   <tr><td>{@code 409}</td><td>a loja não abre por horário, e não há
 *       expediente. ADR-049</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/internal/merchants/{estabelecimentoId}")
@Tag(name = "interno",
        description = "Consumido por outro serviço, com o token de quem pediu encaminhado. "
                + "Não roteado pelo gateway — ADR-045.")
public class ExpedienteCorrenteController {

    private final ConsultarExpedienteCorrente expedientes;

    public ExpedienteCorrenteController(ConsultarExpedienteCorrente expedientes) {
        this.expedientes = expedientes;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O expediente de referência para carimbar"),
            @ApiResponse(responseCode = "400", description = "Identificador da loja malformado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo ativo com a loja, ou loja inexistente — a mesma recusa", content = @Content),
            @ApiResponse(responseCode = "409", description = "A loja não abre por horário (ADR-049 §5)", content = @Content)
    })
    @GetMapping(path = "/expediente-corrente", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "O dia operacional a carimbar numa marcação de disponibilidade",
            description = "Devolve o expediente em curso quando a loja está dentro do horário, "
                    + "e o da próxima abertura quando ela está fechada (ADR-049). "
                    + "409 quando a loja não abre por horário. "
                    + "Exige vínculo ativo, e nenhuma permissão específica.")
    public ExpedienteCorrenteResponse expedienteCorrente(
            @PathVariable UUID estabelecimentoId, @AuthenticationPrincipal Jwt token) {
        return new ExpedienteCorrenteResponse(
                estabelecimentoId,
                expedientes.de(estabelecimentoId, SujeitoDoToken.de(token)));
    }
}
