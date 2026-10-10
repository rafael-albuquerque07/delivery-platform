package com.deliveryplatform.identity.unit;

import com.deliveryplatform.identity.application.port.out.CodificadorDeSenha;
import com.deliveryplatform.identity.application.port.out.UsuarioRepositorio;
import com.deliveryplatform.identity.config.SemeaduraProperties;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.domain.model.Usuario;
import com.deliveryplatform.identity.infrastructure.semeadura.SemeaduraDoIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * O usuário da fixture (ADR-059, W-D). O que importa provar: a senha passa pelo
 * cifrador do cadastro, e a semeadura pela metade não sobe.
 */
@ExtendWith(MockitoExtension.class)
class SemeaduraDoIdentityTest {

    private static final UUID USUARIO = UUID.fromString("5eed0000-0000-4000-8000-000000000003");
    private static final Telefone TELEFONE = Telefone.de("+5511999990001");
    private static final Instant AGORA = Instant.parse("2026-10-10T05:30:00Z");

    @Mock
    UsuarioRepositorio usuarios;

    @Mock
    CodificadorDeSenha codificador;

    @Test
    void desligada_nao_toca_em_nada() {
        semeadura(new SemeaduraProperties(false, null)).run(new DefaultApplicationArguments());

        verifyNoInteractions(usuarios, codificador);
    }

    @Test
    void ligada_sem_senha_nao_sobe_e_a_mensagem_diz_qual_variavel() {
        assertThatThrownBy(() -> new SemeaduraProperties(true, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELIVERY_SEMEADURA_SENHA");
        assertThatThrownBy(() -> new SemeaduraProperties(true, "   "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void senha_que_o_cadastro_recusaria_tambem_nao_sobe_e_nao_aparece_na_mensagem() {
        assertThatThrownBy(() -> new SemeaduraProperties(true, "curta"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("curta");
    }

    @Test
    void o_toString_nao_mostra_a_senha() {
        assertThat(new SemeaduraProperties(true, "uma-senha-qualquer").toString())
                .doesNotContain("uma-senha-qualquer");
    }

    @Test
    void grava_o_usuario_da_fixture_com_a_senha_pelo_cifrador_do_cadastro() {
        when(usuarios.buscarPorId(USUARIO)).thenReturn(Optional.empty());
        when(usuarios.existeComTelefone(TELEFONE)).thenReturn(false);
        when(codificador.codificar("uma-senha-qualquer")).thenReturn("{bcrypt}hash-do-cifrador");

        semeadura(new SemeaduraProperties(true, "uma-senha-qualquer")).run(new DefaultApplicationArguments());

        ArgumentCaptor<Usuario> gravado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).salvar(gravado.capture());
        Usuario usuario = gravado.getValue();
        assertThat(usuario.getId()).isEqualTo(USUARIO);
        assertThat(usuario.getTelefone()).isEqualTo(TELEFONE);
        assertThat(usuario.getHashDaSenha()).isEqualTo("{bcrypt}hash-do-cifrador");
        assertThat(usuario.telefoneVerificado()).isTrue();
    }

    @Test
    void ja_semeado_nao_grava_de_novo() {
        when(usuarios.buscarPorId(USUARIO)).thenReturn(Optional.of(Usuario.reconstituir(
                USUARIO, "Dona da Fixture", TELEFONE, AGORA, null, null, "{bcrypt}x")));

        semeadura(new SemeaduraProperties(true, "uma-senha-qualquer")).run(new DefaultApplicationArguments());

        verify(usuarios, never()).salvar(any());
        verifyNoInteractions(codificador);
    }

    @Test
    void telefone_da_fixture_em_outra_conta_recusa_em_vez_de_trocar_a_senha_de_alguem() {
        when(usuarios.buscarPorId(USUARIO)).thenReturn(Optional.empty());
        when(usuarios.existeComTelefone(TELEFONE)).thenReturn(true);

        assertThatThrownBy(() -> semeadura(new SemeaduraProperties(true, "uma-senha-qualquer"))
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
        verify(usuarios, never()).salvar(any());
    }

    private SemeaduraDoIdentity semeadura(SemeaduraProperties propriedades) {
        return new SemeaduraDoIdentity(propriedades, usuarios, codificador, Clock.fixed(AGORA, ZoneOffset.UTC));
    }
}
