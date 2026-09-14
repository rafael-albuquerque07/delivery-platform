package com.deliveryplatform.identity.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.deliveryplatform.identity.api.dto.SignupRequest;
import com.deliveryplatform.identity.api.dto.SignupResponse;
import com.deliveryplatform.identity.api.dto.VerificationCodeRequest;
import com.deliveryplatform.identity.application.port.in.CadastrarUsuario;
import com.deliveryplatform.identity.application.port.in.SolicitarCodigoDeVerificacao;

import jakarta.validation.Valid;

/**
 * Os dois passos do cadastro, sob {@code /api/v1/auth} (ADR-042 §1).
 *
 * <p><b>Por que debaixo de {@code auth} e não de um prefixo novo.</b> A ADR-012
 * roteia por recurso, e o gateway já manda {@code /api/v1/auth/**} para este
 * serviço. Um prefixo novo exigiria uma rota nova no gateway — e o
 * {@code application.yml} de lá abre com um aviso de que o namespace das rotas
 * <b>nunca foi verificado com o gateway no ar</b>. Acrescentar linha a uma
 * configuração não comprovada é apostar duas vezes; reusar o prefixo que já
 * está lá é apostar zero.
 *
 * <p>E o recurso é o mesmo: criar credencial é do domínio de autenticação, não
 * do domínio do usuário — o {@code Usuario} enquanto pessoa mora em
 * {@code /api/v1/me}, que o gateway também já roteia.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class CadastroController {

    private final SolicitarCodigoDeVerificacao codigos;
    private final CadastrarUsuario cadastros;

    public CadastroController(
            SolicitarCodigoDeVerificacao codigos, CadastrarUsuario cadastros) {
        this.codigos = codigos;
        this.cadastros = cadastros;
    }

    /**
     * <b>202 e corpo vazio, sempre</b> — inclusive para telefone que já tem
     * conta, caso em que nenhum código é criado (ADR-042 §5).
     *
     * <p>202 e não 200: o pedido foi aceito, e o que ele produz — um código na
     * mão do comerciante — acontece fora desta requisição, pelas mãos de quem
     * faz o onboarding. É a semântica exata do código de status, e ela deixa de
     * ser verdade no dia em que houver adaptador de canal: aí vira 202 por
     * assincronia de verdade.
     */
    @PostMapping(path = "/verification-code", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void solicitarCodigo(@Valid @RequestBody VerificationCodeRequest requisicao) {
        codigos.solicitar(requisicao.telefone());
    }

    @PostMapping(path = "/signup", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse cadastrar(@Valid @RequestBody SignupRequest requisicao) {
        return new SignupResponse(cadastros.cadastrar(
                requisicao.telefone(),
                requisicao.codigo(),
                requisicao.nome(),
                requisicao.senha()));
    }
}
