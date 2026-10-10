package com.deliveryplatform.merchant.api.error;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import com.deliveryplatform.merchant.application.exception.CadastroDeLojaRecusado;
import com.deliveryplatform.merchant.application.exception.SemExpedientePorHorario;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Dois tratadores: {@code AcessoNegado} → 403 e {@code SemExpedientePorHorario}
 * → 409 (G-C1). Esta frase dizia "um tratador só" desde a C-B, e a G-C1 pôs o
 * segundo sem emendá-la.
 *
 * <p><b>O contrato não sai daqui.</b> Cada rota declara as próprias recusas em
 * {@code @ApiResponses}, e o {@code ContratoDeErrosIT} prova as duas direções
 * (ADR-053). {@code @ResponseStatus} nestes métodos não mudaria nada: o springdoc
 * o ignora com {@code override-with-generic-response: false}, e em execução quem
 * manda é o status do {@code ProblemDetail}.
 *
 * <p>Este serviço tem sete exceções de domínio escritas — escalada, A2, A3,
 * convite inválido, já pertence à equipe. <b>Nenhuma delas é alcançável por
 * HTTP ainda</b>, porque as quatro rotas que existem são leituras. Escrever os
 * tratadores agora seria escrever sete blocos que nenhuma requisição executa —
 * a peça-que-nunca-rodou em forma de {@code @ExceptionHandler}, e ainda por
 * cima a mais perigosa: um tratador errado só aparece no dia em que a exceção
 * finalmente acontece, que é o pior dia para descobrir.
 *
 * <p>Cada um nasce com a rota que o alcança.
 */
@RestControllerAdvice
public class TratadorDeErros {

    /**
     * 403 para os três casos de M7, com o mesmo corpo.
     *
     * <p><b>403 e não 404</b>, mesmo quando a loja não existe: um 404 aqui
     * diria que aquele identificador não é uma loja, e a diferença entre as duas
     * respostas é um scanner de estabelecimentos escrito em códigos de status.
     */
    @ExceptionHandler(AcessoNegado.class)
    public ProblemDetail acessoNegado(AcessoNegado excecao) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "sem acesso a este estabelecimento");
    }

    /**
     * 409 para a loja que não abre por horário — ADR-049 §5.
     *
     * <p>Nasce com a rota que o alcança, o {@code expediente-corrente}. É
     * <i>estado do mundo</i>, não erro do chamador: a loja existe, e o que não
     * existe é um expediente para carimbar.
     */
    @ExceptionHandler(SemExpedientePorHorario.class)
    public ProblemDetail semExpedientePorHorario(SemExpedientePorHorario excecao) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "a loja não abre por horário");
    }

    /**
     * 400 para a loja que o agregado recusa (ADR-060). Nasce de um ponto só — a
     * construção no {@code CriarEstabelecimentoService} —, e por isso é tipo próprio e
     * não {@code IllegalArgumentException}: um tratador desse tipo transformaria em 400
     * todo defeito que o lançasse.
     *
     * <p>O {@code detail} é a frase do agregado, e a tela a mostra como veio (ADR-055).
     */
    @ExceptionHandler(CadastroDeLojaRecusado.class)
    public ProblemDetail cadastroDeLojaRecusado(CadastroDeLojaRecusado excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, excecao.getMessage());
    }
}
