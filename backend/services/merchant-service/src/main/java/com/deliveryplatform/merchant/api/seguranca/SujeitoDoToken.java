package com.deliveryplatform.merchant.api.seguranca;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * O {@code sub} vira {@code UUID} <b>na borda</b>, e em lugar nenhum depois
 * (ADR-038).
 *
 * <p>Sem esta conversão num ponto só, o identificador do usuário atravessaria o
 * serviço como {@code String} e cada consulta faria a própria conversão — até
 * a que esquecesse. O tipo é o que impede um {@code estabelecimentoId} de ser
 * passado onde se espera um {@code usuarioId}.
 *
 * <p><b>Um {@code sub} que não é UUID vira {@link AcessoNegado}, não 500.</b>
 * Token assinado pela chave certa com um {@code sub} de outro formato não é
 * nosso — é de um sistema que compartilha chave e não compartilha convenção. A
 * resposta é a mesma das outras três recusas de M7.
 */
public final class SujeitoDoToken {

    private SujeitoDoToken() {
    }

    public static UUID de(Jwt token) {
        if (token == null || token.getSubject() == null) {
            throw new AcessoNegado();
        }
        try {
            return UUID.fromString(token.getSubject());
        } catch (IllegalArgumentException naoEhUuid) {
            throw new AcessoNegado();
        }
    }
}
