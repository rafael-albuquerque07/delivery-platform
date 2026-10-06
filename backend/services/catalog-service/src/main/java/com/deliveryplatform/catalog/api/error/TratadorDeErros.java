package com.deliveryplatform.catalog.api.error;

import com.deliveryplatform.catalog.application.exception.AcessoNegado;
import com.deliveryplatform.catalog.application.exception.LojaSemExpediente;
import com.deliveryplatform.catalog.application.port.out.AutorizacaoIndisponivel;
import com.deliveryplatform.catalog.application.port.out.ExpedienteIndisponivel;
import com.deliveryplatform.catalog.domain.exception.RegraDoCatalogoViolada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Seis tratadores, e cada um devolve o código que monta no {@code ProblemDetail}:
 *
 * <ul>
 *   <li>{@code AcessoNegado} e {@code AutorizacaoIndisponivel} → 403;</li>
 *   <li>{@code RegraDoCatalogoViolada} → 400 (G-E);</li>
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
     * 400: o chamador pediu o que o produto não tem — e nunca vai ter. Um
     * {@code SEM_CONTROLE} marcado como esgotado, uma opção que não é daquele
     * produto. É erro dele, não do mundo: a §5 do {@code catalogo.md} dá 400 ao
     * pedido malformado e 409 ao estado que mudou. 400 e não 422, por decisão da
     * G-E (ADR-053): um terceiro código viraria precedente sem que nada o exigisse.
     *
     * <p><b>Até a G-E isto era 500</b>, medido na G-C3a e de novo no
     * {@code ContratoDeErrosIT}, que nasceu vermelho por ele.
     *
     * <p>A mensagem sai inteira: ela nomeia produto, grupo e estado — dado da
     * loja, para quem já passou pela autorização —, e nenhuma nomeia host, caminho
     * ou identificador técnico (conferido na G-E).
     *
     * <p><b>Um caso que este tratador classifica errado, e fica escrito:</b> a
     * mesma exceção sai do {@code Produto.reconstituir} quando um documento
     * gravado está corrompido ("produto sem id"). Isso é defeito do servidor e
     * sairia como 400. Só acontece com o banco estragado; o {@code DocumentoIlegivel}
     * do mapeador, que cobre o caso comum, continua 500.
     */
    @ExceptionHandler(RegraDoCatalogoViolada.class)
    public ProblemDetail regraViolada(RegraDoCatalogoViolada excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, excecao.getMessage());
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
