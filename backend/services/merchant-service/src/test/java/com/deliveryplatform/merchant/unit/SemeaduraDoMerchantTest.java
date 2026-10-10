package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.application.usecase.GerenciarEquipeService;
import com.deliveryplatform.merchant.config.SemeaduraProperties;
import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.infrastructure.semeadura.SemeaduraDoMerchant;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A loja da fixture (ADR-059). O que importa provar é a decisão da ordem: a
 * loja nasce <b>fora</b> do horário e entra nele sozinha, alguns minutos depois.
 * E, desde a W-D, que ela nasce com alguém que consiga entrar nela.
 */
@ExtendWith(MockitoExtension.class)
class SemeaduraDoMerchantTest {

    private static final UUID LOJA = UUID.fromString("5eed0000-0000-4000-8000-000000000001");
    private static final UUID USUARIO = UUID.fromString("5eed0000-0000-4000-8000-000000000003");

    @Mock
    EstabelecimentoRepositorio estabelecimentos;

    @Mock
    MembroRepositorio membros;

    @Mock
    GerenciarEquipeService equipes;

    @Test
    void desligada_nao_toca_no_repositorio() {
        semeadura(false, Instant.parse("2026-10-10T05:30:00Z")).run(new DefaultApplicationArguments());

        verifyNoInteractions(estabelecimentos, membros, equipes);
    }

    @Test
    void a_loja_nasce_fechada_e_abre_sozinha_alguns_minutos_depois() {
        Instant agora = Instant.parse("2026-10-10T05:30:20Z"); // sábado, 02:30 em São Paulo
        when(estabelecimentos.buscarPorId(LOJA)).thenReturn(Optional.empty());

        Estabelecimento loja = semear(agora);

        assertThat(loja.getId()).isEqualTo(LOJA);
        assertThat(loja.estaAberta(agora)).isFalse();
        assertThat(loja.estaAberta(agora.plus(Duration.ofMinutes(3)))).isFalse();
        assertThat(loja.estaAberta(agora.plus(Duration.ofMinutes(5)))).isTrue();
        assertThat(loja.estaAberta(agora.plus(Duration.ofHours(2)))).isTrue();
        // 02:35 de sábado é do dia operacional de sexta — a semeadura não o
        // calcula: quem calcula é o agregado, e é ele que a prova olha.
        assertThat(loja.expedienteParaCarimbo(agora)).contains(LocalDate.of(2026, 10, 9));
    }

    @Test
    void semeada_perto_da_meia_noite_a_faixa_e_do_dia_seguinte() {
        Instant agora = Instant.parse("2026-10-11T02:58:00Z"); // sábado, 23:58 em São Paulo
        when(estabelecimentos.buscarPorId(LOJA)).thenReturn(Optional.empty());

        Estabelecimento loja = semear(agora);

        assertThat(loja.getDisponibilidade().faixasDe(DayOfWeek.SUNDAY)).hasSize(1);
        assertThat(loja.getDisponibilidade().faixasDe(DayOfWeek.SATURDAY)).isEmpty();
        assertThat(loja.estaAberta(agora)).isFalse();
        assertThat(loja.estaAberta(agora.plus(Duration.ofMinutes(5)))).isTrue();
    }

    @Test
    void o_vinculo_nasce_fundador_ativo_e_vai_ao_outbox() {
        Instant agora = Instant.parse("2026-10-10T05:30:00Z");
        when(estabelecimentos.buscarPorId(LOJA)).thenReturn(Optional.empty());
        when(membros.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));

        semeadura(true, agora).run(new DefaultApplicationArguments());

        ArgumentCaptor<Membro> gravado = ArgumentCaptor.forClass(Membro.class);
        verify(membros).salvar(gravado.capture());
        Membro dono = gravado.getValue();
        assertThat(dono.getUsuarioId()).isEqualTo(USUARIO);
        assertThat(dono.getEstabelecimentoId()).isEqualTo(LOJA);
        assertThat(dono.getPapel()).isEqualTo(Papel.ADMINISTRADOR);
        assertThat(dono.getEstado()).isEqualTo(EstadoDoMembro.ATIVO);
        assertThat(dono.pode(Permissao.VER_PRODUTO)).isTrue();
        assertThat(dono.pode(Permissao.ALTERAR_PRODUTO)).isTrue();
        verify(equipes).registrarVinculoNascido(dono, agora);
    }

    @Test
    void loja_da_I_C_sem_vinculo_ganha_so_o_vinculo() {
        when(estabelecimentos.buscarPorId(LOJA)).thenReturn(Optional.of(LojaDeTeste.pizzaria()));
        when(membros.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));

        semeadura(true, Instant.parse("2026-10-10T05:30:00Z")).run(new DefaultApplicationArguments());

        verify(estabelecimentos, never()).salvar(any());
        verify(membros).salvar(any());
        verify(equipes).registrarVinculoNascido(any(), any());
    }

    @Test
    void ja_semeada_nao_grava_de_novo() {
        when(estabelecimentos.buscarPorId(LOJA)).thenReturn(Optional.of(LojaDeTeste.pizzaria()));
        when(membros.buscarPorUsuarioELoja(eq(USUARIO), eq(LOJA)))
                .thenReturn(Optional.of(Membro.fundador(USUARIO, LOJA, Instant.parse("2026-10-01T00:00:00Z"))));

        semeadura(true, Instant.parse("2026-10-10T05:30:00Z")).run(new DefaultApplicationArguments());

        verify(estabelecimentos, never()).salvar(any());
        verify(membros, never()).salvar(any());
        verifyNoInteractions(equipes);
    }

    private Estabelecimento semear(Instant agora) {
        semeadura(true, agora).run(new DefaultApplicationArguments());
        ArgumentCaptor<Estabelecimento> gravado = ArgumentCaptor.forClass(Estabelecimento.class);
        verify(estabelecimentos).salvar(gravado.capture());
        return gravado.getValue();
    }

    private SemeaduraDoMerchant semeadura(boolean ligada, Instant agora) {
        return new SemeaduraDoMerchant(
                new SemeaduraProperties(ligada), estabelecimentos, membros, equipes,
                Clock.fixed(agora, ZoneOffset.UTC));
    }
}
