package com.deliveryplatform.identity.unit;

import com.deliveryplatform.identity.domain.model.Email;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.domain.model.Usuario;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioTest {

    private static final Telefone TELEFONE = Telefone.de("+5511987654321");

    @Test
    void nasce_com_telefone_verificado_e_sem_email() {
        Usuario usuario = Usuario.novo("Marli", TELEFONE, Instant.now(), "hash");

        assertThat(usuario.telefoneVerificado()).isTrue();
        assertThat(usuario.getEmail()).isNull();
        assertThat(usuario.emailVerificado()).isFalse();
    }

    @Test
    void nao_existe_usuario_com_telefone_nao_verificado() {
        assertThatThrownBy(() -> Usuario.novo("Marli", TELEFONE, null, "hash"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("U4");
    }

    @Test
    void nome_em_branco_e_invalido() {
        assertThatThrownBy(() -> Usuario.novo("   ", TELEFONE, Instant.now(), "hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Usuario.novo(null, TELEFONE, Instant.now(), "hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nome_e_aparado() {
        Usuario usuario = Usuario.novo("  Marli  ", TELEFONE, Instant.now(), "hash");
        assertThat(usuario.getNome()).isEqualTo("Marli");
    }

    @Test
    void email_novo_nasce_nao_verificado() {
        Usuario usuario = Usuario.novo("Marli", TELEFONE, Instant.now(), "hash");

        usuario.adicionarEmail(new Email("marli@example.com"));

        assertThat(usuario.getEmail()).isEqualTo(new Email("marli@example.com"));
        assertThat(usuario.emailVerificado()).isFalse();
    }

    @Test
    void verificar_email_sem_ter_cadastrado_falha() {
        Usuario usuario = Usuario.novo("Marli", TELEFONE, Instant.now(), "hash");

        assertThatThrownBy(() -> usuario.verificarEmail(Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void email_verificado_registra_o_instante() {
        Usuario usuario = Usuario.novo("Marli", TELEFONE, Instant.now(), "hash");
        usuario.adicionarEmail(new Email("marli@example.com"));

        usuario.verificarEmail(Instant.now());

        assertThat(usuario.emailVerificado()).isTrue();
    }

    @Test
    void toString_nao_vaza_hash_email_nem_nome() {
        Usuario usuario = Usuario.novo("Marli Aparecida", TELEFONE, Instant.now(), "segredo-super-hash");
        usuario.adicionarEmail(new Email("marli@example.com"));

        String texto = usuario.toString();

        assertThat(texto)
                .doesNotContain("segredo-super-hash")
                .doesNotContain("marli@example.com")
                .doesNotContain("Marli");
    }
}
