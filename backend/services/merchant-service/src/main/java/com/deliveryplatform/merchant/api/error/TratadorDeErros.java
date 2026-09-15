package com.deliveryplatform.merchant.api.error;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Um tratador só, e é de propósito.
 *
 * <p>Este serviço tem sete exceções de domínio escritas — escalada, A2, A3,
 * convite inválido, já pertence à equipe. <b>Nenhuma delas é alcançável por
 * HTTP ainda</b>, porque a única rota que existe é uma leitura. Escrever os
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
}
