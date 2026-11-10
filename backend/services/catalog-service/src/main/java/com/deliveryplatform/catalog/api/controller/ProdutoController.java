package com.deliveryplatform.catalog.api.controller;

import com.deliveryplatform.catalog.api.dto.GrupoRequest;
import com.deliveryplatform.catalog.api.dto.MarcacaoRequest;
import com.deliveryplatform.catalog.api.dto.OpcaoRequest;
import com.deliveryplatform.catalog.api.dto.PaginaResponse;
import com.deliveryplatform.catalog.api.dto.ProdutoRequest;
import com.deliveryplatform.catalog.api.dto.ProdutoResponse;
import com.deliveryplatform.catalog.api.dto.ProdutoResumoResponse;
import com.deliveryplatform.catalog.application.port.in.ConsultarProduto;
import com.deliveryplatform.catalog.application.port.in.CriarProduto;
import com.deliveryplatform.catalog.application.port.in.DespublicarProduto;
import com.deliveryplatform.catalog.application.port.in.ListarProdutos;
import com.deliveryplatform.catalog.application.port.in.MarcarDisponibilidade;
import com.deliveryplatform.catalog.application.port.in.MontarOpcoes;
import com.deliveryplatform.catalog.application.port.in.PublicarProduto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import com.deliveryplatform.valuetypes.Money;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
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
    private final ConsultarProduto consultas;
    private final MarcarDisponibilidade marcacoes;
    private final CriarProduto criacao;
    private final PublicarProduto publicacao;
    private final MontarOpcoes montagem;
    private final DespublicarProduto despublicacao;

    public ProdutoController(ListarProdutos produtos, ConsultarProduto consultas,
                             MarcarDisponibilidade marcacoes, CriarProduto criacao,
                             PublicarProduto publicacao, MontarOpcoes montagem,
                             DespublicarProduto despublicacao) {
        this.produtos = produtos;
        this.consultas = consultas;
        this.marcacoes = marcacoes;
        this.criacao = criacao;
        this.publicacao = publicacao;
        this.montagem = montagem;
        this.despublicacao = despublicacao;
    }

    /**
     * O comerciante cria um grupo de opções no produto, vazio (ADR-077).
     *
     * <p><b>201 sem {@code Location}</b>: não há {@code GET} de grupo, e a regra é
     * <i>"nunca prometer rota que não existe"</i> (ADR-065 §3). O corpo é o produto
     * inteiro — o grupo novo vem nele, com o id que a rota da opção vai citar.
     *
     * <p>O 400 é corpo malformado, C3 ({@code minEscolhas} e {@code maxEscolhas}), ou,
     * com o produto já no cardápio, a recusa da publicação recobrada: um grupo vazio
     * não alcança o próprio teto (C4). <b>Sem 409 declarado</b>, como na publicação: a
     * gravação concorrente ainda pode dar o do {@code @Version} (ADR-052), e provocá-lo
     * exigiria costura no código de produção.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "O produto, com o grupo novo"),
            @ApiResponse(responseCode = "400", description = "Corpo malformado, limites do grupo (C3), ou, com o produto publicado, a recusa da publicação recobrada (C4); ou identificador malformado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem ALTERAR_PRODUTO, loja inexistente ou merchant indisponível — a mesma recusa", content = @Content),
            @ApiResponse(responseCode = "404", description = "O produto não existe nesta loja — inexistente ou de outra loja, a mesma resposta (ADR-056)", content = @Content)
    })
    @PostMapping(path = "/{produtoId}/grupos",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cria um grupo de opções no produto",
            description = "Exige ALTERAR_PRODUTO. O grupo nasce vazio. Com o produto já "
                    + "publicado, as invariantes da publicação são recobradas, e um grupo "
                    + "vazio é recusado (C4). Devolve o produto inteiro.")
    public ResponseEntity<ProdutoResponse> criarGrupo(
            @PathVariable UUID estabelecimentoId,
            @PathVariable UUID produtoId,
            @Valid @RequestBody GrupoRequest pedido) {

        var produto = montagem.acrescentarGrupo(estabelecimentoId, produtoId,
                new MontarOpcoes.NovoGrupo(pedido.nome(), pedido.minEscolhas(),
                        pedido.maxEscolhas(), pedido.ordem()));

        return ResponseEntity.status(201).body(ProdutoResponse.de(produto));
    }

    /**
     * O comerciante acrescenta uma opção a um grupo do produto (ADR-077). Mesma forma
     * da de cima: 201, sem {@code Location}, com o produto inteiro.
     *
     * <p>O 404 cobre o produto que não é desta loja e o grupo que não é deste produto.
     * Com o produto publicado, o 400 costuma ser a C2: um acréscimo negativo que faz
     * existir uma combinação de graça.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "O produto, com a opção nova"),
            @ApiResponse(responseCode = "400", description = "Corpo malformado, ou, com o produto publicado, a recusa da publicação recobrada (C2); ou identificador malformado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem ALTERAR_PRODUTO, loja inexistente ou merchant indisponível — a mesma recusa", content = @Content),
            @ApiResponse(responseCode = "404", description = "O produto não existe nesta loja (ADR-056), ou o grupo não existe neste produto", content = @Content)
    })
    @PostMapping(path = "/{produtoId}/grupos/{grupoId}/opcoes",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Acrescenta uma opção a um grupo do produto",
            description = "Exige ALTERAR_PRODUTO. A opção nasce disponível, e o acréscimo "
                    + "aceita zero e negativo. Com o produto já publicado, as invariantes da "
                    + "publicação são recobradas. Devolve o produto inteiro.")
    public ResponseEntity<ProdutoResponse> criarOpcao(
            @PathVariable UUID estabelecimentoId,
            @PathVariable UUID produtoId,
            @PathVariable UUID grupoId,
            @Valid @RequestBody OpcaoRequest pedido) {

        var produto = montagem.acrescentarOpcao(estabelecimentoId, produtoId, grupoId,
                new MontarOpcoes.NovaOpcao(pedido.nome(), Money.de(pedido.acrescimo()),
                        pedido.ordem()));

        return ResponseEntity.status(201).body(ProdutoResponse.de(produto));
    }

    /**
     * Leva o produto ao cardápio (ADR-065). <b>{@code PUT} em sub-recurso, sem corpo</b>,
     * na forma da marcação: o único estado que a publicação pode pôr é {@code ATIVO}, e
     * repetir é inofensivo — 200 nas duas chamadas, com o estado que vale.
     *
     * <p>O 400 é a recusa da publicação — preço zero (C1), grupo impossível (C5), teto
     * inalcançável (C4), combinação sem preço positivo (C2) —, com a frase que nomeia o
     * produto ou os grupos e não o código da invariante (ADR-063). Ou identificador
     * malformado.
     *
     * <p><b>Sem 409 declarado</b>, por decisão da ADR-065 §5: repetir não é conflito. A
     * gravação concorrente do mesmo produto ainda pode dar o 409 do {@code @Version}
     * (ADR-052) — e, como na marcação, provocá-lo exigiria costura no código de produção.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O produto, publicado — também quando já estava"),
            @ApiResponse(responseCode = "400", description = "A publicação recusada (C1, C5, C4, C2), com a frase que diz o que corrigir; ou identificador malformado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem ALTERAR_PRODUTO, loja inexistente ou merchant indisponível — a mesma recusa", content = @Content),
            @ApiResponse(responseCode = "404", description = "O produto não existe nesta loja — inexistente ou de outra loja, a mesma resposta (ADR-056)", content = @Content)
    })
    @PutMapping(path = "/{produtoId}/publicacao", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Publica o produto no cardápio",
            description = "Exige ALTERAR_PRODUTO. Idempotente: publicar o que já está publicado "
                    + "responde 200. As invariantes C1, C5, C4 e C2 são cobradas aqui.")
    public ProdutoResponse publicar(
            @PathVariable UUID estabelecimentoId,
            @PathVariable UUID produtoId) {

        return ProdutoResponse.de(publicacao.publicar(estabelecimentoId, produtoId));
    }

    /**
     * Tira o produto do cardápio (ADR-078): o par da de cima. O {@code PUT} põe a
     * publicação, o {@code DELETE} a retira — <b>o produto não é apagado</b> (C9); ele
     * fica {@code INATIVO}, fora do cardápio, com o histórico preservado.
     *
     * <p>Exige {@code DESATIVAR_PRODUTO}, que o {@code merchant} publica desde o primeiro
     * enum. Repetir é inofensivo, como na publicação: 200 com o estado que vale.
     *
     * <p>O <b>409</b> é o rascunho, que nunca apareceu e não tem o que tirar: o pedido está
     * bem formado e o estado do produto o proíbe (E-8, ADR-053 emendada). O 400 é só
     * identificador malformado.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O produto, fora do cardápio — também quando já estava"),
            @ApiResponse(responseCode = "400", description = "Identificador malformado", content = @Content),
            @ApiResponse(responseCode = "409", description = "O produto é rascunho e nunca apareceu no cardápio — recusa de estado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem DESATIVAR_PRODUTO, loja inexistente ou merchant indisponível — a mesma recusa", content = @Content),
            @ApiResponse(responseCode = "404", description = "O produto não existe nesta loja — inexistente ou de outra loja, a mesma resposta (ADR-056)", content = @Content)
    })
    @DeleteMapping(path = "/{produtoId}/publicacao", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Tira o produto do cardápio",
            description = "Exige DESATIVAR_PRODUTO. O produto fica INATIVO e não é apagado. "
                    + "Idempotente: tirar do ar o que já saiu responde 200. Rascunho é recusado.")
    public ProdutoResponse despublicar(
            @PathVariable UUID estabelecimentoId,
            @PathVariable UUID produtoId) {

        return ProdutoResponse.de(despublicacao.despublicar(estabelecimentoId, produtoId));
    }

    /**
     * O produto nasce <b>rascunho</b> (ADR-065): ir ao cardápio é um segundo ato, com
     * regras próprias. Sem grupos — eles entram na E-3d.
     *
     * <p><b>201 com {@code Location}</b>, porque há para onde apontar: o {@code GET} do
     * produto por id existe desde a G-F. O precedente das duas criações anteriores — loja
     * e categoria, sem {@code Location} — é <i>"nunca prometer rota que não existe"</i>, e
     * aqui ela existe (ADR-065 §3). O caminho é relativo, o mesmo que passa pelo gateway.
     *
     * <p><b>Sem 404</b>: o único identificador da URL é a loja, e loja sem vínculo é 403
     * (M7). A categoria que não resolve vem no corpo e é 400 (ADR-066).
     */
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "O produto criado, em rascunho"),
            @ApiResponse(responseCode = "400", description = "Corpo malformado, preço negativo (C1), ou seção que não existe nesta loja (ADR-066)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem CRIAR_PRODUTO, loja inexistente ou merchant indisponível — a mesma recusa", content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cria um produto, em rascunho",
            description = "Exige CRIAR_PRODUTO. A seção tem de ser desta loja: inexistente e "
                    + "de outra loja recebem a mesma recusa. Location aponta para o GET do "
                    + "produto.")
    public ResponseEntity<ProdutoResponse> criar(
            @PathVariable UUID estabelecimentoId,
            @Valid @RequestBody ProdutoRequest pedido) {

        var criado = criacao.criar(estabelecimentoId, new CriarProduto.Pedido(
                pedido.categoriaId(), pedido.nome(), pedido.descricao(),
                Money.de(pedido.precoBase()), pedido.modoDeControle(), pedido.ordem()));

        return ResponseEntity
                .created(URI.create("/api/v1/merchants/" + estabelecimentoId
                        + "/catalog/produtos/" + criado.getId()))
                .body(ProdutoResponse.de(criado));
    }

    /**
     * Um produto da loja, inteiro — com os grupos e as opções, que é o que a tela da
     * opção precisa para chamar a marcação (G-F).
     *
     * <p>Qualquer estado de publicação sai, rascunho inclusive: esta é a tela de agir
     * sobre um item, e a listagem é a vitrine. <b>404</b> quando o produto não existe
     * <b>ou é de outra loja</b>, com o mesmo corpo — o identificador da URL nunca é
     * confiado (invariante 9), e um 403 aqui diria "existe, mas não é seu".
     *
     * <p>Sem 409 e sem 503: esta rota não grava e não pergunta o expediente.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O produto, com grupos e opções"),
            @ApiResponse(responseCode = "400", description = "Identificador malformado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem VER_PRODUTO, loja inexistente ou merchant indisponível — a mesma recusa", content = @Content),
            @ApiResponse(responseCode = "404", description = "O produto não existe nesta loja — inexistente ou de outra loja, a mesma resposta", content = @Content)
    })
    @GetMapping(path = "/{produtoId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Um produto do estabelecimento, com os grupos e as opções",
            description = "Exige VER_PRODUTO. Devolve o produto em qualquer estado de "
                    + "publicação. 404 para produto inexistente ou de outra loja, com o "
                    + "mesmo corpo.")
    public ProdutoResponse consultar(
            @PathVariable UUID estabelecimentoId,
            @PathVariable UUID produtoId) {

        return ProdutoResponse.de(consultas.consultar(estabelecimentoId, produtoId));
    }

    /**
     * Os produtos da loja, em <b>qualquer estado de publicação</b>, paginados (ADR-068).
     *
     * <p><b>A listagem é a do painel.</b> Até a E-3d ela filtrava {@code ATIVO}, como se
     * fosse a vitrine — e a rota exige {@code VER_PRODUTO}, que só comerciante tem. O
     * cardápio de quem compra entra pela porta da ADR-018. Com o produto nascendo
     * rascunho (E-3c), o filtro escondia do comerciante o que ele acabara de cadastrar.
     *
     * <p><b>Nem filtra por vendável.</b> O comerciante precisa ver exatamente o que não
     * está vendável para poder agir — a pizza cujo grupo "Tamanho" esgotou inteiro
     * aparece, com {@code vendavel: false}. E cada item traz {@code estadoDePublicacao},
     * porque com rascunho na lista {@code vendavel: false} sozinho confunde "nunca foi
     * publicado" com "acabou tudo".
     *
     * <p><b>A ordem padrão é {@code ordem}, depois {@code id}</b> — o adaptador a aplica
     * quando não vem {@code sort}; quem manda {@code sort} leva o que pediu.
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
            @ApiResponse(responseCode = "200", description = "A página de produtos da loja, em qualquer estado de publicação"),
            @ApiResponse(responseCode = "400", description = "Identificador ou paginação malformados", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem VER_PRODUTO, loja inexistente ou merchant indisponível — a mesma recusa", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lista os produtos do estabelecimento, em qualquer estado de publicação",
            description = "A listagem do painel: rascunho, ativo e inativo, cada um com o "
                    + "estadoDePublicacao. Sem sort, pela ordem e depois pelo id. Exige vínculo "
                    + "ativo com VER_PRODUTO, resolvido no merchant-service com o token de quem "
                    + "pediu. Sem vínculo, sem permissão, loja inexistente e merchant "
                    + "indisponível devolvem a mesma recusa.")
    public PaginaResponse<ProdutoResumoResponse> listar(
            @PathVariable UUID estabelecimentoId,
            @ParameterObject @PageableDefault(size = 20) Pageable paginacao) {

        return PaginaResponse.de(
                produtos.daLoja(estabelecimentoId, paginacao),
                ProdutoResumoResponse::de);
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O produto recalculado"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido, ou estado que o modo do produto não tem (SEM_CONTROLE não acaba)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem ALTERAR_PRODUTO, loja inexistente ou merchant indisponível — a mesma recusa", content = @Content),
            @ApiResponse(responseCode = "404", description = "O produto não existe nesta loja — inexistente ou de outra loja, a mesma resposta (ADR-056)", content = @Content),
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
            @ApiResponse(responseCode = "400", description = "Corpo ou identificador malformado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem vínculo, sem ALTERAR_PRODUTO, loja inexistente ou merchant indisponível — a mesma recusa", content = @Content),
            @ApiResponse(responseCode = "404", description = "O produto não existe nesta loja (ADR-056), ou o grupo e a opção não existem neste produto (ADR-063 §4)", content = @Content),
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
