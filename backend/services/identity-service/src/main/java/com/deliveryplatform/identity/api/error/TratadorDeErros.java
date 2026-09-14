package com.deliveryplatform.identity.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.deliveryplatform.identity.application.exception.CadastroRecusado;
import com.deliveryplatform.identity.application.exception.CredenciaisInvalidas;
import com.deliveryplatform.identity.domain.exception.TelefoneInvalido;

/**
 * Corpo idêntico para todo motivo de recusa.
 *
 * <p>Nenhuma das três exceções carrega qual dos casos ocorreu, e este tratador
 * não teria como diferenciá-los nem se quisesse — que é o desenho. Erro de
 * formato do corpo (JSON quebrado, campo ausente) continua sendo 400 pelo
 * tratamento padrão do Spring MVC: requisição malformada não é tentativa de
 * login nem de cadastro, e devolver 401/400-de-recusa para ela esconderia um
 * defeito do cliente atrás da política de indistinguibilidade.
 */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(CredenciaisInvalidas.class)
    public ProblemDetail credenciaisInvalidas(CredenciaisInvalidas excecao) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "telefone ou senha inválidos");
    }

    /**
     * 400, não 401: cadastro não é uma tentativa de autenticação — é o cliente
     * pedindo algo que não pode ser concedido com o que ele mandou (ADR-042
     * §5).
     */
    @ExceptionHandler(CadastroRecusado.class)
    public ProblemDetail cadastroRecusado(CadastroRecusado excecao) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "código de verificação inválido ou expirado");
    }

    /**
     * A exceção deliberada da ADR-042 §5: telefone que não normaliza é 400, não
     * a recusa genérica. Um número malformado não podia estar cadastrado de
     * forma nenhuma, então responder como se fosse uma tentativa de cadastro
     * esconderia um erro do cliente atrás de uma política que existe para
     * outra coisa. No login (ADR-037 §7) o mesmo caso vira 401, porque lá a
     * indistinguibilidade é o próprio ponto — aqui não é.
     *
     * <p>Mensagem fixa, não {@code excecao.getMessage()}: a exceção carrega o
     * valor recebido, e ecoar exceção de domínio direto na borda é como um
     * detalhe interno vaza pra API sem ninguém decidir isso.
     */
    @ExceptionHandler(TelefoneInvalido.class)
    public ProblemDetail telefoneInvalido(TelefoneInvalido excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "telefone inválido");
    }
}
