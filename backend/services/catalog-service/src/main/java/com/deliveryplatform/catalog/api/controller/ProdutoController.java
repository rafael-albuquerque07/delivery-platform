package com.deliveryplatform.catalog.api.controller;

import com.deliveryplatform.catalog.api.dto.MarcacaoRequest;
import com.deliveryplatform.catalog.api.dto.PaginaResponse;
import com.deliveryplatform.catalog.api.dto.ProdutoResumoResponse;
import com.deliveryplatform.catalog.application.port.in.ListarProdutos;
import com.deliveryplatform.catalog.application.port.in.MarcarDisponibilidade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * A primeira rota do {@code catalog-service}. Até esta classe existir, o
 * serviço tinha nove classes de domínio, um {@code changeUnit}, persistência
 * com transação — e {@code api/} com quatro pastas vazias.
 *
 * <h2>O caminho, e cada pedaço dele</h2>
 *
 * <pre>
 * GET /api/v1/merchants/{estabelecimentoId}/catalog/produtos
 * </pre>
 *
 * <p>É o que a ADR-012 já reservou no gateway: o predicado
 * {@code Path=/api/v1/merchants/{merchantId}/catalog/**} está lá desde o commit
 * inicial, roteando para a porta 8083, e o {@code RoteamentoIT} da E-A já prova
 * que qualquer coisa sob esse prefixo chega aqui sem reescrita. <b>Nenhuma linha
 * nova de gateway nesta rodada.</b>
 *
 * <p>A mistura de idiomas é a regra, e não um descuido. O {@code CLAUDE.md}:
 * <i>"Prefixo e serviço em inglês (…) Identificador e recurso em português"</i>.
 * Aqui {@code catalog} é o segmento do serviço, {@code estabelecimentoId} é o
 * identificador e {@code produtos} é o recurso.
 *
 * <h2>O {@code estabelecimentoId} da URL é entrada, não contexto</h2>
 *
 * <p>Ele não vira filtro de consulta nenhuma antes de passar pelo caso de uso,
 * que o manda ao {@code merchant} junto com o token do portador. É a mesma
 * invariante 9 que o {@code EquipeController} materializa — com a diferença de
 * que aqui quem confronta o identificador com o vínculo é outro serviço.
 */
@RestController
@RequestMapping("/api/v1/merchants/{estabelecimentoId}/catalog/produtos")
public class ProdutoController {

    private final ListarProdutos produtos;
    private final MarcarDisponibilidade marcacoes;

    public ProdutoController(ListarProdutos produtos, MarcarDisponibilidade marcacoes) {
        this.produtos = produtos;
        this.marcacoes = marcacoes;
    }

    /**
     * Os produtos publicados da loja, paginados.
     *
     * <p><b>Publicados, e não vendáveis.</b> O comerciante precisa ver
     * exatamente o que não está vendável para poder agir — a pizza cujo grupo
     * "Tamanho" esgotou inteiro aparece na lista, com {@code vendavel: false}.
     * Esconder seria a tela deixar de mostrar o problema que ela existe para
     * resolver.
     *
     * <p>O tamanho padrão é vinte, e o teto vem de
     * {@code spring.data.web.pageable.max-page-size} — sem ele, um
     * {@code ?size=1000000} vira uma consulta que carrega o cardápio inteiro na
     * memória e um corpo que ninguém consegue renderizar.
     *
     * <p><b>O {@code @ParameterObject} é o que faz o contrato ser usável.</b>
     * Sem ele o springdoc descreve o {@code Pageable} como um parâmetro de
     * consulta só, chamado {@code paginacao}, do tipo objeto e obrigatório — um
     * contrato que nenhum cliente consegue seguir, para uma rota que funciona
     * sem parâmetro nenhum. Com ele saem {@code page}, {@code size} e
     * {@code sort}, opcionais, que é o que a rota de fato lê.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A página de produtos publicados"),
            @ApiResponse(responseCode = "400", description = "Identificador ou paginação malformados", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem VER_PRODUTO, loja inexistente ou merchant indisponível — a mesma recusa", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lista os produtos publicados do estabelecimento",
            description = "Exige vínculo ativo com VER_PRODUTO, resolvido no merchant-service "
                    + "com o token de quem pediu. Sem vínculo, sem permissão, loja inexistente "
                    + "e merchant indisponível devolvem a mesma recusa.")
    public PaginaResponse<ProdutoResumoResponse> listar(
            @PathVariable UUID estabelecimentoId,
            @ParameterObject @PageableDefault(size = 20) Pageable paginacao) {

        return PaginaResponse.de(
                produtos.publicadosDaLoja(estabelecimentoId, paginacao),
                ProdutoResumoResponse::de);
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O produto recalculado"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido, ou estado que o modo do produto não tem (SEM_CONTROLE não acaba)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem ALTERAR_PRODUTO, produto de outra loja ou merchant indisponível — a mesma recusa", content = @Content),
            @ApiResponse(responseCode = "409", description = "ESGOTADO_HOJE numa loja que não abre por horário (ADR-049), ou o produto mudou durante a marcação (ADR-052)", content = @Content),
            @ApiResponse(responseCode = "503", description = "O merchant não respondeu sobre o expediente", content = @Content)
    })
    @PutMapping(path = "/{produtoId}/disponibilidade",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Marca a disponibilidade do produto",
            description = "O carimbo é do servidor: o instante pelo relógio do serviço e o "
                    + "expediente perguntado ao merchant (ADR-049). Exige ALTERAR_PRODUTO. "
                    + "409 quando a loja não abre por horário e o estado é ESGOTADO_HOJE. "
                    + "Devolve o produto recalculado.")
    public ProdutoResumoResponse marcarProduto(
            @PathVariable UUID estabelecimentoId,
            @PathVariable UUID produtoId,
            @Valid @RequestBody MarcacaoRequest pedido) {

        return ProdutoResumoResponse.de(
                marcacoes.deProduto(estabelecimentoId, produtoId, pedido.estado()));
    }

    /**
     * O par da de cima, para a opção — e é a que mais precisa da resposta com
     * corpo: marcar a última opção disponível de um grupo obrigatório derruba o
     * {@code vendavel} do produto, e a tela não consegue derivar isso sozinha.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O produto recalculado"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido, ou opção que não pertence a este produto", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem ALTERAR_PRODUTO, produto de outra loja ou merchant indisponível — a mesma recusa", content = @Content),
            @ApiResponse(responseCode = "409", description = "ESGOTADO_HOJE numa loja que não abre por horário (ADR-049), ou o produto mudou durante a marcação (ADR-052)", content = @Content),
            @ApiResponse(responseCode = "503", description = "O merchant não respondeu sobre o expediente", content = @Content)
    })
    @PutMapping(path = "/{produtoId}/grupos/{grupoId}/opcoes/{opcaoId}/disponibilidade",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Marca a disponibilidade de uma opção do produto",
            description = "Mesmo carimbo e mesmas recusas da marcação do produto. Devolve o "
                    + "produto recalculado, porque marcar uma opção pode derrubar o vendavel.")
    public ProdutoResumoResponse marcarOpcao(
            @PathVariable UUID estabelecimentoId,
            @PathVariable UUID produtoId,
            @PathVariable UUID grupoId,
            @PathVariable UUID opcaoId,
            @Valid @RequestBody MarcacaoRequest pedido) {

        return ProdutoResumoResponse.de(marcacoes.deOpcao(
                estabelecimentoId, produtoId, grupoId, opcaoId, pedido.estado()));
    }
}
