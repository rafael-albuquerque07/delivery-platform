package com.deliveryplatform.catalog.api.error;

import com.deliveryplatform.catalog.application.exception.AcessoNegado;
import com.deliveryplatform.catalog.application.exception.LojaSemExpediente;
import com.deliveryplatform.catalog.application.exception.ProdutoNaoEncontrado;
import com.deliveryplatform.catalog.application.port.out.AutorizacaoIndisponivel;
import com.deliveryplatform.catalog.application.port.out.ExpedienteIndisponivel;
import com.deliveryplatform.catalog.domain.exception.GrupoNaoEncontrado;
import com.deliveryplatform.catalog.domain.exception.OpcaoNaoEncontrada;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Nove tratadores, e cada um devolve o código que monta no {@code ProblemDetail}:
 *
 * <ul>
 *   <li>{@code AcessoNegado} e {@code AutorizacaoIndisponivel} → 403;</li>
 *   <li>{@code RegraDoCatalogoViolada} → 400 quando o motivo é regra, 500 quando é
 *       {@code INTEGRIDADE} (G-E; a separação desde a E-3b, ADR-063), e 409 para
 *       {@code RASCUNHO_NAO_SE_INATIVA}, que é recusa de estado (E-8, ADR-053 emendada);</li>
 *   <li>{@code ProdutoNaoEncontrado} → 404 (G-F; a marcação desde a G-G, ADR-056);</li>
 *   <li>{@code OpcaoNaoEncontrada} → 404 (E-3b, ADR-063 §4);</li>
 *   <li>{@code GrupoNaoEncontrado} → 404 (E-6, ADR-077);</li>
 *   <li>{@code LojaSemExpediente} → 409 (G-C2);</li>
 *   <li>{@code OptimisticLockingFailureException} → 409 (G-C3a, ADR-052);</li>
 *   <li>{@code ExpedienteIndisponivel} → 503 (G-C2).</li>
 * </ul>
 *
 * <p><b>O contrato não sai daqui.</b> O que cada rota declara está nela, em
 * {@code @ApiResponses} (ADR-053), e o {@code ContratoDeErrosIT} prova as duas
 * direções. Um tratador novo que não apareça em nenhuma rota é recusa que o
 * contrato esconde; o teste a acha quando alguém a provoca. E {@code @ResponseStatus}
 * aqui não serve: com {@code springdoc.override-with-generic-response: false} o
 * springdoc o ignora, e em execução quem manda é o status do {@code ProblemDetail}
 * — medido na G-E.
 *
 * <p>Os dois primeiros respondem <b>a mesma coisa</b>. Isso não é redundância: é onde a distinção entre "negado" e "não
 * respondeu" encontra a borda. Para o cliente as duas são 403 com o mesmo
 * corpo — falha fechada, e nenhuma pista de qual das duas foi. Para quem opera,
 * a segunda é um registro em nível de alerta, porque significa que o
 * {@code merchant} está fora e <b>o catálogo inteiro parou de autorizar</b>.
 *
 * <p>A ADR-011 já tinha assumido esse custo com todas as letras: <i>"se o
 * merchant-service cair por mais de um minuto, a plataforma inteira para de
 * autorizar. A resposta a isso é disponibilidade — réplicas, health check,
 * alerta — não relaxar a regra"</i>. O alerta é esta linha de registro.
 */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    private static final String RECUSA = "sem acesso a este estabelecimento";

    /**
     * 403 para as quatro recusas, com o mesmo corpo.
     *
     * <p><b>403 e não 404</b>, mesmo quando a loja não existe: a diferença
     * entre as duas respostas é um scanner de estabelecimentos escrito em
     * códigos de status (M7).
     */
    @ExceptionHandler(AcessoNegado.class)
    public ProblemDetail acessoNegado(AcessoNegado excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, RECUSA);
    }

    /**
     * O mesmo 403 — e um registro que diz que não foi o usuário, foi o sistema.
     *
     * <p>Se esta linha aparecer em rajada, nenhum comerciante está conseguindo
     * abrir o cardápio, e a causa não está no catálogo.
     */
    @ExceptionHandler(AutorizacaoIndisponivel.class)
    public ProblemDetail autorizacaoIndisponivel(AutorizacaoIndisponivel excecao) {
        log.warn("autorização indisponível: {}", excecao.getMessage(), excecao);
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, RECUSA);
    }

    /**
     * 404: o produto não existe nesta loja — inexistente, ou de outra loja (G-F, ADR-056). A
     * mensagem da exceção é fixa de propósito e sai inteira: ela não diz qual dos
     * dois casos aconteceu.
     */
    @ExceptionHandler(ProdutoNaoEncontrado.class)
    public ProblemDetail produtoNaoEncontrado(ProdutoNaoEncontrado excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, excecao.getMessage());
    }

    /**
     * 409: a loja não abre por horário, e {@code ESGOTADO_HOJE} nunca
     * reativaria (ADR-049 §5). A mensagem da exceção é de domínio e diz o que
     * fazer — ela sai inteira.
     */
    @ExceptionHandler(LojaSemExpediente.class)
    public ProblemDetail lojaSemExpediente(LojaSemExpediente excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, excecao.getMessage());
    }

    /**
     * 503, e não 403: a autorização já passou, então dizer que o expediente
     * está indisponível não revela nada sobre a loja. A mensagem é fixa — a da
     * exceção pode nomear o host, e endereço de serviço não sai na resposta.
     */
    @ExceptionHandler(ExpedienteIndisponivel.class)
    public ProblemDetail expedienteIndisponivel(ExpedienteIndisponivel excecao) {
        log.warn("expediente indisponível: {}", excecao.getMessage(), excecao);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "não foi possível consultar o expediente");
    }

    /**
     * 404: a opção não existe neste produto (ADR-063 §4). É o 404 do produto um nível
     * abaixo, e a frase é escrita aqui: não diz se faltou o grupo ou a opção.
     */
    @ExceptionHandler(OpcaoNaoEncontrada.class)
    public ProblemDetail opcaoNaoEncontrada(OpcaoNaoEncontrada excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "esta opção não existe mais neste produto; recarregue o produto");
    }

    /**
     * 404: o grupo onde a opção nova entraria não é deste produto (ADR-077). A frase é
     * escrita aqui, e é fixa.
     */
    @ExceptionHandler(GrupoNaoEncontrado.class)
    public ProblemDetail grupoNaoEncontrado(GrupoNaoEncontrado excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "este grupo não existe mais neste produto; recarregue o produto");
    }

    /**
     * 400 quando o comerciante pediu o que o catálogo não aceita; <b>500 quando quem
     * errou fomos nós</b> (ADR-063).
     *
     * <p><b>Regra → 400</b>, com a frase escrita aqui a partir do motivo e dos dados —
     * nunca a mensagem da exceção, que carrega o código da invariante ({@code C5}) e foi
     * escrita para o log (W-E). 400 e não 422, por decisão da G-E (ADR-053).
     *
     * <p><b>{@code INTEGRIDADE} → 500</b>, com frase genérica, e a mensagem técnica vai
     * ao log e <b>não</b> ao corpo: <i>"produto sem id"</i> diz a quem lê como o nosso
     * modelo se chama por dentro. Até a E-3b estes dezoito pontos saíam 400 — a figura
     * que o {@code CLAUDE.md} recusa para o tratador de {@code IllegalArgumentException}.
     *
     * <p><b>{@code RASCUNHO_NAO_SE_INATIVA} → 409</b> (E-8). Tirar do ar um rascunho é um
     * pedido bem formado que o estado do produto proíbe — estado do mundo, pela §3 da
     * ADR-053, e transição fora da tabela, pela convenção do {@code CLAUDE.md}. Até a E-8 ele
     * saía 400 só por ser desta classe: o status vinha do <b>tipo</b> da exceção, e o tipo
     * não sabe se o caso é do chamador ou do mundo. O motivo sabe. A frase não mudou.
     *
     * <p><b>O documento corrompido não passa por aqui</b> (G-C3b): o
     * {@code ProdutoMapper} converte a exceção em {@code DocumentoIlegivel}, que não tem
     * tratador e sai como 500.
     */
    @ExceptionHandler(RegraDoCatalogoViolada.class)
    public ProblemDetail regraViolada(RegraDoCatalogoViolada excecao) {
        if (excecao.motivo() == RegraDoCatalogoViolada.Motivo.INTEGRIDADE) {
            log.warn("invariante de construção quebrada: {}", excecao.getMessage(), excecao);
            return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                    "não foi possível completar a operação");
        }
        return ProblemDetail.forStatusAndDetail(statusDe(excecao.motivo()), fraseDe(excecao));
    }

    /** O status de cada motivo de regra: 400, menos a recusa de estado, que é 409. */
    static HttpStatus statusDe(RegraDoCatalogoViolada.Motivo motivo) {
        return motivo == RegraDoCatalogoViolada.Motivo.RASCUNHO_NAO_SE_INATIVA
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
    }

    /**
     * A frase de cada motivo. <b>Sem {@code default}</b>: motivo novo quebra a compilação
     * aqui, em vez de cair numa frase genérica que ninguém escreveu para ele.
     */
    static String fraseDe(RegraDoCatalogoViolada excecao) {
        List<String> d = excecao.dados();
        return switch (excecao.motivo()) {
            case TEXTO_OBRIGATORIO -> "o " + d.get(0) + " é obrigatório";
            case LIMITES_DO_GRUPO -> "o grupo «" + d.get(0) + "» tem limites de escolha "
                    + "impossíveis: o mínimo vai de zero até o máximo, e o máximo é pelo menos 1";
            case PRECO_BASE_INVALIDO -> "o preço do produto não pode ser negativo; para dar "
                    + "desconto, use um acréscimo negativo numa opção";
            case PUBLICACAO_SEM_PRECO -> "«" + d.get(0) + "» está com preço zero e não vai ao "
                    + "cardápio; ponha o preço e publique de novo";
            case PUBLICACAO_GRUPO_IMPOSSIVEL -> grupos(d) + " pede" + plural(d, "", "m")
                    + " mais escolhas do que " + plural(d, "tem", "têm") + " opções, e ninguém "
                    + "conseguiria pedir o produto; acrescente opções ou diminua o mínimo";
            case PUBLICACAO_TETO_INALCANCAVEL -> grupos(d) + " deixa" + plural(d, "", "m")
                    + " escolher mais opções do que " + plural(d, "tem", "têm")
                    + "; diminua o máximo ou acrescente opções";
            case PUBLICACAO_PRECO_NAO_POSITIVO -> "com os acréscimos das opções, o produto "
                    + "pode sair por R$ " + d.get(0).replace('.', ',') + ", e o preço precisa "
                    + "ser maior que zero; ajuste os acréscimos negativos";
            case RASCUNHO_NAO_SE_INATIVA -> "este produto ainda é rascunho e nunca apareceu no "
                    + "cardápio; não há o que tirar do ar";
            case CATEGORIA_NAO_ENCONTRADA -> "a seção escolhida não está no cardápio desta "
                    + "loja; escolha uma das seções da lista";
            case MODO_SEM_CONTROLE -> "«" + d.get(1) + "» não tem controle de disponibilidade e não "
                    + "aceita a marcação «" + estado(d.get(0)) + "»";
            case INTEGRIDADE -> "não foi possível completar a operação";
        };
    }

    private static String grupos(List<String> nomes) {
        String lista = "«" + String.join("», «", nomes) + "»";
        return nomes.size() == 1 ? "o grupo " + lista : "os grupos " + lista;
    }

    private static String plural(List<String> nomes, String um, String varios) {
        return nomes.size() == 1 ? um : varios;
    }

    private static String estado(String nome) {
        return switch (EstadoDeDisponibilidade.valueOf(nome)) {
            case DISPONIVEL -> "disponível";
            case ACABANDO -> "acabando";
            case ESGOTADO_HOJE -> "esgotado hoje";
            case ESGOTADO_INDETERMINADO -> "esgotado";
        };
    }

    /**
     * 409: o produto foi gravado por outra pessoa entre a leitura e a gravação
     * desta requisição (ADR-052). Não é 500 — o servidor não errou — e não é
     * 400 — o pedido estava certo quando foi feito. O cliente que recarrega
     * acerta. A mensagem é fixa: a da exceção nomeia id e versão.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail produtoMudou(OptimisticLockingFailureException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "o produto mudou enquanto você marcava; recarregue e tente de novo");
    }
}
