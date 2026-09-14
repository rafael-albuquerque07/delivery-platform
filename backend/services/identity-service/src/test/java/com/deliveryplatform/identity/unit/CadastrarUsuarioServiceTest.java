package com.deliveryplatform.identity.unit;

import com.deliveryplatform.identity.application.exception.CadastroRecusado;
import com.deliveryplatform.identity.application.port.out.CodificadorDeSenha;
import com.deliveryplatform.identity.application.port.out.CodigoDeVerificacaoRepositorio;
import com.deliveryplatform.identity.application.port.out.UsuarioRepositorio;
import com.deliveryplatform.identity.application.usecase.CadastrarUsuarioService;
import com.deliveryplatform.identity.domain.exception.TelefoneInvalido;
import com.deliveryplatform.identity.domain.model.CodigoDeVerificacao;
import com.deliveryplatform.identity.domain.model.Telefone;
import com.deliveryplatform.identity.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CadastrarUsuarioServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-14T12:00:00Z");
    private static final Telefone CANONICO = new Telefone("+5511987654321");
    private static final String BRUTO = "11987654321";
    private static final String CERTO = "004291";
    private static final String HASH = "{bcrypt}$da-marli";

    @Mock
    UsuarioRepositorio usuarios;

    @Mock
    CodigoDeVerificacaoRepositorio codigos;

    @Mock
    CodificadorDeSenha codificador;

    CadastrarUsuarioService servico;

    @BeforeEach
    void montar() {
        servico = new CadastrarUsuarioService(
                usuarios, codigos, codificador, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private CodigoDeVerificacao codigoValido() {
        return CodigoDeVerificacao.novo(CANONICO, CERTO, AGORA);
    }

    private void haCodigo(CodigoDeVerificacao codigo) {
        when(codigos.buscarPorTelefone(CANONICO)).thenReturn(Optional.of(codigo));
    }

    // ── caminho feliz ───────────────────────────────────────────────────────

    @Test
    void o_codigo_certo_cria_a_conta_e_o_telefone_nasce_verificado_no_instante_da_conferencia() {
        haCodigo(codigoValido());
        when(usuarios.existeComTelefone(CANONICO)).thenReturn(false);
        when(codificador.codificar("senha-da-marli")).thenReturn(HASH);
        when(usuarios.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        servico.cadastrar(BRUTO, CERTO, "Marli", "senha-da-marli");

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).salvar(salvo.capture());

        assertThat(salvo.getValue().getNome()).isEqualTo("Marli");
        assertThat(salvo.getValue().getTelefone()).isEqualTo(CANONICO);
        assertThat(salvo.getValue().getTelefoneVerificadoEm())
                .as("o carimbo da U4 é o instante em que o código foi conferido — é o "
                        + "que torna a invariante verdadeira em vez de afirmada")
                .isEqualTo(AGORA);
        assertThat(salvo.getValue().getHashDaSenha())
                .as("nunca a senha em claro — U5")
                .isEqualTo(HASH);
        assertThat(salvo.getValue().getEmail())
                .as("e-mail é o segundo canal, pedido depois")
                .isNull();
    }

    @Test
    void o_codigo_usado_e_removido() {
        haCodigo(codigoValido());
        when(usuarios.existeComTelefone(CANONICO)).thenReturn(false);
        when(codificador.codificar(any())).thenReturn(HASH);
        when(usuarios.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        servico.cadastrar(BRUTO, CERTO, "Marli", "senha-da-marli");

        verify(codigos).removerDe(CANONICO);
    }

    @Test
    void o_telefone_e_normalizado_antes_de_procurar_o_codigo() {
        haCodigo(codigoValido());
        when(usuarios.existeComTelefone(CANONICO)).thenReturn(false);
        when(codificador.codificar(any())).thenReturn(HASH);
        when(usuarios.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        servico.cadastrar("(11) 98765-4321", CERTO, "Marli", "senha-da-marli");

        verify(codigos).buscarPorTelefone(CANONICO);
    }

    // ── recusas, todas iguais ───────────────────────────────────────────────

    @Test
    void sem_codigo_pedido_recusa() {
        when(codigos.buscarPorTelefone(CANONICO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.cadastrar(BRUTO, CERTO, "Marli", "senha-da-marli"))
                .isInstanceOf(CadastroRecusado.class);

        verify(usuarios, never()).salvar(any());
    }

    @Test
    void codigo_errado_recusa_e_a_tentativa_gasta_e_persistida() {
        CodigoDeVerificacao codigo = codigoValido();
        haCodigo(codigo);

        assertThatThrownBy(() -> servico.cadastrar(BRUTO, "999999", "Marli", "senha-da-marli"))
                .isInstanceOf(CadastroRecusado.class);

        ArgumentCaptor<CodigoDeVerificacao> regravado =
                ArgumentCaptor.forClass(CodigoDeVerificacao.class);
        verify(codigos).substituir(regravado.capture());

        assertThat(regravado.getValue().getTentativas())
                .as("se o contador não sair da memória, o limite de cinco nunca chega a "
                        + "um e a força bruta sai de graça — é por isso que o caso de uso "
                        + "não é @Transactional")
                .isEqualTo(1);
        verify(usuarios, never()).salvar(any());
    }

    @Test
    void codigo_expirado_recusa() {
        CodigoDeVerificacao vencido = CodigoDeVerificacao.novo(
                CANONICO, CERTO, AGORA.minus(CodigoDeVerificacao.VALIDADE));
        haCodigo(vencido);

        assertThatThrownBy(() -> servico.cadastrar(BRUTO, CERTO, "Marli", "senha-da-marli"))
                .isInstanceOf(CadastroRecusado.class);
    }

    @Test
    void codigo_queimado_por_tentativas_recusa_mesmo_estando_certo() {
        CodigoDeVerificacao codigo = codigoValido();
        for (int i = 0; i < CodigoDeVerificacao.TENTATIVAS_MAXIMAS; i++) {
            codigo.confere("000000", AGORA);
        }
        haCodigo(codigo);

        assertThatThrownBy(() -> servico.cadastrar(BRUTO, CERTO, "Marli", "senha-da-marli"))
                .isInstanceOf(CadastroRecusado.class);
    }

    @Test
    void telefone_que_ganhou_conta_no_meio_do_caminho_recebe_a_mesma_recusa() {
        haCodigo(codigoValido());
        when(usuarios.existeComTelefone(CANONICO)).thenReturn(true);

        assertThatThrownBy(() -> servico.cadastrar(BRUTO, CERTO, "Marli", "senha-da-marli"))
                .as("um motivo diferente aqui seria o oráculo de cadastro entrando pela "
                        + "porta dos fundos")
                .isInstanceOf(CadastroRecusado.class);

        verify(usuarios, never()).salvar(any());
    }

    @Test
    void a_corrida_perdida_no_indice_unico_vira_a_mesma_recusa_e_nao_um_500() {
        haCodigo(codigoValido());
        when(usuarios.existeComTelefone(CANONICO)).thenReturn(false);
        when(codificador.codificar(any())).thenReturn(HASH);
        when(usuarios.salvar(any())).thenThrow(new DataIntegrityViolationException("uq_usuario_telefone"));

        assertThatThrownBy(() -> servico.cadastrar(BRUTO, CERTO, "Marli", "senha-da-marli"))
                .as("dois cliques no botão não podem produzir um 500")
                .isInstanceOf(CadastroRecusado.class);
    }

    @Test
    void telefone_impossivel_de_normalizar_reclama_e_nao_vira_recusa_generica() {
        assertThatThrownBy(() -> servico.cadastrar("abc", CERTO, "Marli", "senha-da-marli"))
                .isInstanceOf(TelefoneInvalido.class);
    }
}
