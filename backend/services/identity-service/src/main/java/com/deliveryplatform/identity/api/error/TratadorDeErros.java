package com.deliveryplatform.identity.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.deliveryplatform.identity.application.exception.CredenciaisInvalidas;

/**
 * Corpo idêntico para todo motivo de recusa.
 *
 * <p>A exceção não carrega qual dos três casos ocorreu, e este tratador não
 * teria como diferenciá-los nem se quisesse — que é o desenho. Erro de formato
 * do corpo (JSON quebrado, campo ausente) continua sendo 400 pelo tratamento
 * padrão do Spring MVC: requisição malformada não é tentativa de login, e
 * devolver 401 para ela esconderia um defeito do cliente atrás da política de
 * indistinguibilidade.
 */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(CredenciaisInvalidas.class)
    public ProblemDetail credenciaisInvalidas(CredenciaisInvalidas excecao) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "telefone ou senha inválidos");
    }
}
