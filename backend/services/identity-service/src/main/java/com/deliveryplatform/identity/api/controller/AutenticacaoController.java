package com.deliveryplatform.identity.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deliveryplatform.identity.api.dto.LoginRequest;
import com.deliveryplatform.identity.api.dto.LoginResponse;
import com.deliveryplatform.identity.application.port.in.AutenticarUsuario;
import com.deliveryplatform.identity.application.port.out.TokenEmitido;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacaoController {

    private final AutenticarUsuario autenticar;

    public AutenticacaoController(AutenticarUsuario autenticar) {
        this.autenticar = autenticar;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest requisicao) {
        TokenEmitido token = autenticar.autenticar(requisicao.telefone(), requisicao.senha());

        return new LoginResponse(token.valor(), "Bearer", token.validadeEmSegundos());
    }
}
