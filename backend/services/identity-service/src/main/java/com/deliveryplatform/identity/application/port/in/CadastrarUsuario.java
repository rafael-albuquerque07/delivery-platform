package com.deliveryplatform.identity.application.port.in;

import java.util.UUID;

/**
 * Segundo passo do cadastro: o código é conferido e a conta nasce, na mesma
 * chamada (ADR-042 §1).
 *
 * <p><b>Um passo só, e não "confirmar" e depois "cadastrar".</b> Dois passos
 * exigiriam um estado intermediário — um código confirmado esperando virar
 * conta — que é uma chave de cadastro parada no banco com validade própria. Com
 * uma chamada, a janela do código é a única janela que existe.
 *
 * <p>Devolve o {@code id} e não um token. Emitir token aqui criaria um segundo
 * lugar que emite token, e a ADR-037 tem um. Quem acabou de se cadastrar faz
 * login com a senha que acabou de escolher.
 */
public interface CadastrarUsuario {

    UUID cadastrar(String telefoneBruto, String codigo, String nome, String senha);
}
