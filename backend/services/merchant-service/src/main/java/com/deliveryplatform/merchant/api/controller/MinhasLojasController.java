package com.deliveryplatform.merchant.api.controller;

import com.deliveryplatform.merchant.api.seguranca.SujeitoDoToken;
import com.deliveryplatform.merchant.application.port.in.ConsultarMinhasLojas;
import com.deliveryplatform.merchant.application.port.in.LojaDoUsuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

/**
 * A primeira rota que o front chama depois de entrar — e a que faltava para o
 * painel existir.
 *
 * <pre>
 * GET /api/v1/me/estabelecimentos
 * </pre>
 *
 * <h2>Não há identificador no caminho, e a ausência é a decisão</h2>
 *
 * <p>Todas as outras rotas de negócio deste sistema começam com
 * {@code /api/v1/merchants/{estabelecimentoId}/…}, e a invariante 9 manda
 * confrontar esse identificador com o usuário autenticado. <b>Esta é a rota que
 * responde qual identificador usar</b> — ela não pode receber um, e é por isso
 * que mora sob {@code /me}.
 *
 * <p>Pela mesma razão ela <b>não exige permissão nenhuma</b>: pedir permissão
 * numa loja para descobrir de quais lojas se faz parte é circular. O que a torna
 * segura é a outra metade, idêntica à da G-B2: <b>não existe entrada</b>. O
 * usuário sai do {@code sub} do token e de mais lugar nenhum, então não há como
 * fazer esta rota falar de outra pessoa.
 *
 * <h2>Mora no {@code merchant}, e o gateway precisou de uma linha</h2>
 *
 * <p>O gateway roteia {@code /api/v1/me/**} para o {@code identity} desde o
 * primeiro commit — e o {@code identity} nunca teve controlador nenhum sob
 * {@code /me}. O vínculo mora aqui. A G-B5 acrescenta um predicado
 * <b>específico</b> acima do genérico, seguindo a regra que o próprio arquivo
 * do gateway escreve: <i>"A ORDEM IMPORTA: o primeiro predicado que casa vence.
 * Rotas mais específicas primeiro"</i>. O {@code /me} continua sendo do
 * {@code identity} para tudo o mais.
 *
 * <h2>Lista vazia, e não 404</h2>
 *
 * <p>Quem não tem vínculo ativo em loja nenhuma recebe {@code 200} com
 * {@code []}. É o estado de todo mundo no instante seguinte ao cadastro, e é
 * resposta, não ausência de recurso — o recurso é "as minhas lojas", e ele
 * existe mesmo vazio.
 */
@RestController
@RequestMapping("/api/v1/me/estabelecimentos")
@Tag(name = "eu", description = "O que o portador do token é, neste serviço.")
public class MinhasLojasController {

    private final ConsultarMinhasLojas lojas;

    public MinhasLojasController(ConsultarMinhasLojas lojas) {
        this.lojas = lojas;
    }

    /**
     * O {@code sub} vira {@code UUID} na borda, pelo {@link SujeitoDoToken} — o
     * mesmo conversor do {@code EquipeController} e do
     * {@code ContextoDeAcessoController} (ADR-038).
     *
     * <p>Um {@code sub} que não seja {@code UUID} vira {@code AcessoNegado}, e
     * portanto 403 — não 500, que é o que um {@code UUID.fromString} solto daria.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "As lojas em que o portador tem vínculo ativo"),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "As lojas de que o portador do token faz parte",
            description = "Só vínculos ATIVOS. Traz papel e permissões de cada uma, "
                    + "para o front montar o seletor de loja e o menu numa chamada só. "
                    + "Lista vazia quando não há vínculo ativo em nenhuma loja.")
    public List<LojaDoUsuario> minhasLojas(@AuthenticationPrincipal Jwt token) {
        return lojas.de(SujeitoDoToken.de(token));
    }
}
