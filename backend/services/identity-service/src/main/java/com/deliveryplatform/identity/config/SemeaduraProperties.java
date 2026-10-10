package com.deliveryplatform.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * A bandeira da semeadura de desenvolvimento, e a senha do usuário da fixture
 * (ADR-059).
 *
 * <p><b>Falsa por padrão.</b> Ligada, ela <b>exige</b> a senha — vinda de
 * {@code DELIVERY_SEMEADURA_SENHA}, nunca de uma constante — e recusa a subida
 * sem ela: semeadura pela metade, com loja e vínculo e sem ninguém que consiga
 * entrar, é pior que nenhuma.
 *
 * <p>O tamanho é o do {@code SignupRequest}, 8 a 72: uma senha que o cadastro
 * recusaria não é senha que alguém deveria conseguir usar. 72 é o teto do bcrypt.
 *
 * <p><b>A mensagem de recusa nunca carrega a senha</b>, e o {@link #toString()}
 * a esconde: um record imprime todos os campos, e propriedade aparece em log de
 * erro de ligação.
 */
@ConfigurationProperties(prefix = "delivery.semeadura")
public record SemeaduraProperties(@DefaultValue("false") boolean ligada, String senha) {

    public SemeaduraProperties {
        if (ligada && (senha == null || senha.isBlank())) {
            throw new IllegalStateException(
                    "delivery.semeadura.ligada=true exige a senha do usuário da fixture em "
                            + "DELIVERY_SEMEADURA_SENHA — semeadura pela metade é pior que nenhuma");
        }
        if (ligada && (senha.length() < 8 || senha.length() > 72)) {
            throw new IllegalStateException(
                    "DELIVERY_SEMEADURA_SENHA precisa ter de 8 a 72 caracteres, como no cadastro");
        }
    }

    @Override
    public String toString() {
        return "SemeaduraProperties[ligada=" + ligada + ", senha=" + (senha == null ? "ausente" : "***") + "]";
    }
}
