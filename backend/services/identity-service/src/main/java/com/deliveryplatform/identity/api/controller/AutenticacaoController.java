package com.deliveryplatform.identity.api.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deliveryplatform.identity.api.dto.LoginRequest;
import com.deliveryplatform.identity.api.dto.LoginResponse;
import com.deliveryplatform.identity.application.port.in.AutenticarUsuario;
import com.deliveryplatform.identity.application.port.out.TokenEmitido;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;

// Pública de propósito: sem token (o javadoc do OpenApiConfig). O `security: []`
// vazio diz isso no contrato, e o redocly do workflow `contracts` confere (V-1).
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacaoController {

    private final AutenticarUsuario autenticar;

    public AutenticacaoController(AutenticarUsuario autenticar) {
        this.autenticar = autenticar;
    }

    @Operation(summary = "Entra no painel com telefone e senha, e recebe o token")
    @PostMapping(path = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginResponse login(@Valid @RequestBody LoginRequest requisicao) {
        TokenEmitido token = autenticar.autenticar(requisicao.telefone(), requisicao.senha());

        return new LoginResponse(token.valor(), "Bearer", token.validadeEmSegundos());
    }
}
