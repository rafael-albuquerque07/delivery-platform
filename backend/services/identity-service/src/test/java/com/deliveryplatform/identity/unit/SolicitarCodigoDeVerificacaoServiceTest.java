package com.deliveryplatform.identity.unit;

import com.deliveryplatform.identity.application.port.out.CodigoDeVerificacaoRepositorio;
import com.deliveryplatform.identity.application.port.out.GeradorDeCodigo;
import com.deliveryplatform.identity.application.port.out.UsuarioRepositorio;
import com.deliveryplatform.identity.application.usecase.SolicitarCodigoDeVerificacaoService;
import com.deliveryplatform.identity.domain.exception.TelefoneInvalido;
import com.deliveryplatform.identity.domain.model.CodigoDeVerificacao;
import com.deliveryplatform.identity.domain.model.Telefone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitarCodigoDeVerificacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-14T12:00:00Z");
    private static final Telefone CANONICO = new Telefone("+5511987654321");

    @Mock
    UsuarioRepositorio usuarios;

    @Mock
    CodigoDeVerificacaoRepositorio codigos;

    @Mock
    GeradorDeCodigo gerador;

    SolicitarCodigoDeVerificacaoService servico;

    @BeforeEach
    void montar() {
        servico = new SolicitarCodigoDeVerificacaoService(
                usuarios, codigos, gerador, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void telefone_sem_conta_ganha_um_codigo_com_o_instante_do_relogio_injetado() {
        when(usuarios.existeComTelefone(CANONICO)).thenReturn(false);
        when(gerador.gerar()).thenReturn("004291");

        servico.solicitar("11987654321");

        ArgumentCaptor<CodigoDeVerificacao> gravado =
                ArgumentCaptor.forClass(CodigoDeVerificacao.class);
        verify(codigos).substituir(gravado.capture());

        assertThat(gravado.getValue().getCodigo()).isEqualTo("004291");
        assertThat(gravado.getValue().getTelefone()).isEqualTo(CANONICO);
        assertThat(gravado.getValue().getCriadoEm())
                .as("o instante vem do Clock, não de Instant.now() — é o que torna "
                        + "a expiração testável sem sleep")
                .isEqualTo(AGORA);
        assertThat(gravado.getValue().getTentativas()).isZero();
    }

    @Test
    void o_telefone_e_normalizado_antes_de_qualquer_coisa() {
        when(usuarios.existeComTelefone(CANONICO)).thenReturn(false);
        when(gerador.gerar()).thenReturn("123456");

        servico.solicitar("(11) 98765-4321");

        verify(usuarios).existeComTelefone(CANONICO);
    }

    @Test
    void telefone_que_ja_tem_conta_nao_gera_codigo_e_nao_reclama() {
        when(usuarios.existeComTelefone(CANONICO)).thenReturn(true);

        servico.solicitar("11987654321");

        verifyNoInteractions(codigos);
        verify(gerador, never()).gerar();
    }

    @Test
    void a_resposta_e_a_mesma_nos_dois_casos_porque_nao_ha_resposta() {
        // A assinatura devolve void, e isso é a decisão, não uma economia: um
        // boolean aqui viraria um oráculo de cadastro na borda, e a varredura
        // de DDDs sairia de graça (ADR-042 §5).
        assertThat(SolicitarCodigoDeVerificacaoService.class.getMethods())
                .filteredOn(metodo -> metodo.getName().equals("solicitar"))
                .allMatch(metodo -> metodo.getReturnType() == void.class);
    }

    @Test
    void telefone_impossivel_de_normalizar_reclama_em_vez_de_ficar_calado() {
        assertThatThrownBy(() -> servico.solicitar("abc"))
                .as("número malformado não podia estar cadastrado de forma nenhuma — "
                        + "responder 202 esconderia um erro do cliente atrás de uma "
                        + "política que existe para outra coisa")
                .isInstanceOf(TelefoneInvalido.class);

        verifyNoInteractions(usuarios, codigos, gerador);
    }
}
