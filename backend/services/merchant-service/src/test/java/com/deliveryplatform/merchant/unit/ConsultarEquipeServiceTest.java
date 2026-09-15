package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.application.usecase.ConsultarEquipeService;
import com.deliveryplatform.merchant.domain.model.Equipe;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.support.EquipeDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarEquipeServiceTest {

    private static final UUID LOJA = UUID.randomUUID();

    @Mock
    MembroRepositorio membros;

    ConsultarEquipeService servico;

    @BeforeEach
    void montar() {
        servico = new ConsultarEquipeService(membros);
    }

    @Test
    void quem_tem_gerenciar_equipe_recebe_a_equipe() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Equipe equipe = Equipe.de(LOJA, List.of(marli));
        when(membros.buscarPorUsuarioELoja(marli.getUsuarioId(), LOJA))
                .thenReturn(Optional.of(marli));
        when(membros.equipeDe(LOJA)).thenReturn(equipe);

        assertThat(servico.daLoja(LOJA, marli.getUsuarioId())).isSameAs(equipe);
    }

    @Test
    void a_equipe_e_lida_sem_cadeado() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        when(membros.buscarPorUsuarioELoja(any(), any())).thenReturn(Optional.of(marli));
        when(membros.equipeDe(LOJA)).thenReturn(Equipe.de(LOJA, List.of(marli)));

        servico.daLoja(LOJA, marli.getUsuarioId());

        verify(membros, never()).equipeParaAlteracao(any());
    }

    @Test
    void sem_vinculo_e_acesso_negado() {
        when(membros.buscarPorUsuarioELoja(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.daLoja(LOJA, UUID.randomUUID()))
                .as("mesma recusa de quem pediu uma loja que não existe — a consulta que "
                        + "falha é a mesma")
                .isInstanceOf(AcessoNegado.class);

        verify(membros, never()).equipeDe(any());
    }

    @Test
    void vinculo_sem_a_permissao_e_acesso_negado() {
        Membro bia = EquipeDeTeste.bia(LOJA);
        when(membros.buscarPorUsuarioELoja(any(), any())).thenReturn(Optional.of(bia));

        assertThatThrownBy(() -> servico.daLoja(LOJA, bia.getUsuarioId()))
                .isInstanceOf(AcessoNegado.class);

        verify(membros, never()).equipeDe(any());
    }

    @Test
    void vinculo_suspenso_com_a_permissao_tambem_e_negado() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        Membro junior = EquipeDeTeste.junior(LOJA);
        Equipe.de(LOJA, List.of(marli, junior)).suspender(marli, junior, EquipeDeTeste.AGORA);
        when(membros.buscarPorUsuarioELoja(any(), any())).thenReturn(Optional.of(junior));

        assertThatThrownBy(() -> servico.daLoja(LOJA, junior.getUsuarioId()))
                .as("o `pode` exige estado ATIVO; a permissão guardada não basta")
                .isInstanceOf(AcessoNegado.class);
    }

    @Test
    void a_busca_do_vinculo_usa_o_par_usuario_loja_e_nao_so_a_loja() {
        Membro marli = EquipeDeTeste.marli(LOJA);
        when(membros.buscarPorUsuarioELoja(marli.getUsuarioId(), LOJA))
                .thenReturn(Optional.of(marli));
        when(membros.equipeDe(LOJA)).thenReturn(Equipe.de(LOJA, List.of(marli)));

        servico.daLoja(LOJA, marli.getUsuarioId());

        verify(membros).buscarPorUsuarioELoja(marli.getUsuarioId(), LOJA);
    }
}
