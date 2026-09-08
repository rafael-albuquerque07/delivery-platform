package com.deliveryplatform.identity.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deliveryplatform.identity.application.exception.CredenciaisInvalidas;
import com.deliveryplatform.identity.application.port.out.CodificadorDeSenha;
import com.deliveryplatform.identity.application.port.out.EmissorDeToken;
import com.deliveryplatform.identity.application.port.out.TokenEmitido;
import com.deliveryplatform.identity.application.port.out.UsuarioRepositorio;
import com.deliveryplatform.identity.application.usecase.AutenticarUsuarioService;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.domain.model.Usuario;

@ExtendWith(MockitoExtension.class)
class AutenticarUsuarioServiceTest {

    private static final String HASH_DESCARTAVEL = "{bcrypt}$descartavel";
    private static final String HASH_DA_MARLI = "{bcrypt}$da-marli";
    private static final String TELEFONE = "11987654321";

    @Mock
    UsuarioRepositorio usuarios;

    @Mock
    CodificadorDeSenha codificador;

    @Mock
    EmissorDeToken emissor;

    AutenticarUsuarioService servico;
    Usuario marli;

    @BeforeEach
    void montar() {
        when(codificador.codificar(anyString())).thenReturn(HASH_DESCARTAVEL);
        servico = new AutenticarUsuarioService(usuarios, codificador, emissor);
        marli = Usuario.novo("Marli", Telefone.de(TELEFONE), Instant.now(), HASH_DA_MARLI);
    }

    @Test
    void credenciais_certas_emitem_token() {
        Instant agora = Instant.now();
        TokenEmitido esperado = new TokenEmitido("o.token.aqui", agora, agora.plusSeconds(1800));

        when(usuarios.buscarPorTelefone(Telefone.de(TELEFONE))).thenReturn(Optional.of(marli));
        when(codificador.confere("senha-certa", HASH_DA_MARLI)).thenReturn(true);
        when(emissor.emitir(marli.getId())).thenReturn(esperado);

        assertThat(servico.autenticar(TELEFONE, "senha-certa")).isEqualTo(esperado);
    }

    @Test
    void o_telefone_e_normalizado_antes_da_busca() {
        when(usuarios.buscarPorTelefone(new Telefone("+5511987654321"))).thenReturn(Optional.of(marli));
        when(codificador.confere(anyString(), anyString())).thenReturn(true);
        when(emissor.emitir(any())).thenReturn(
                new TokenEmitido("t", Instant.now(), Instant.now().plusSeconds(1800)));

        servico.autenticar("(11) 98765-4321", "senha-certa");

        verify(usuarios).buscarPorTelefone(new Telefone("+5511987654321"));
    }

    @Test
    void senha_errada_e_credencial_invalida() {
        when(usuarios.buscarPorTelefone(any())).thenReturn(Optional.of(marli));
        when(codificador.confere("senha-errada", HASH_DA_MARLI)).thenReturn(false);

        assertThatThrownBy(() -> servico.autenticar(TELEFONE, "senha-errada"))
                .isInstanceOf(CredenciaisInvalidas.class);

        verifyNoInteractions(emissor);
    }

    @Test
    void telefone_inexistente_e_credencial_invalida() {
        when(usuarios.buscarPorTelefone(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar(TELEFONE, "qualquer"))
                .isInstanceOf(CredenciaisInvalidas.class);

        verifyNoInteractions(emissor);
    }

    @Test
    void telefone_inexistente_ainda_paga_o_custo_de_conferir_a_senha() {
        when(usuarios.buscarPorTelefone(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar(TELEFONE, "qualquer"))
                .isInstanceOf(CredenciaisInvalidas.class);

        verify(codificador).confere("qualquer", HASH_DESCARTAVEL);
    }

    @Test
    void telefone_impossivel_de_normalizar_nao_chega_ao_banco() {
        assertThatThrownBy(() -> servico.autenticar("isto não é telefone", "qualquer"))
                .as("normalização impossível é credencial inválida, não erro de formato")
                .isInstanceOf(CredenciaisInvalidas.class);

        verifyNoInteractions(usuarios);
        verifyNoInteractions(emissor);
        verify(codificador).confere("qualquer", HASH_DESCARTAVEL);
    }

    @Test
    void nenhum_caminho_de_falha_emite_token() {
        when(usuarios.buscarPorTelefone(any())).thenReturn(Optional.of(marli));
        when(codificador.confere(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> servico.autenticar(TELEFONE, "x"))
                .isInstanceOf(CredenciaisInvalidas.class);

        verify(emissor, never()).emitir(any());
    }
}
