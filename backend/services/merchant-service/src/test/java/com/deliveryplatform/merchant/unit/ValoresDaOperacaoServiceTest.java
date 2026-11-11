package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import com.deliveryplatform.merchant.application.exception.OperacaoRecusada;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.application.usecase.DefinirDescontoDeRetiradaService;
import com.deliveryplatform.merchant.application.usecase.DefinirPedidoMinimoService;
import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Papel;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.merchant.support.EquipeDeTeste;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
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
 * Os dois casos de uso da E-9 (ADR-080): autorizam antes de buscar, exigem
 * {@code CONFIGURAR_LOJA}, e traduzem a recusa do domínio num ponto só — sem conferir a regra.
 */
@ExtendWith(MockitoExtension.class)
class ValoresDaOperacaoServiceTest {

    private static final UUID LOJA = UUID.randomUUID();
    private static final UUID AUTOR = UUID.randomUUID();

    @Mock MembroRepositorio membros;
    @Mock EstabelecimentoRepositorio estabelecimentos;

    private static Membro vinculoCom(Permissao permissao) {
        return Membro.reconstituir(UUID.randomUUID(), AUTOR, LOJA, Papel.COLABORADOR,
                EnumSet.of(permissao), EstadoDoMembro.ATIVO, EquipeDeTeste.AGORA, EquipeDeTeste.AGORA);
    }

    private DefinirPedidoMinimoService minimos() {
        return new DefinirPedidoMinimoService(membros, estabelecimentos);
    }

    private DefinirDescontoDeRetiradaService descontos() {
        return new DefinirDescontoDeRetiradaService(membros, estabelecimentos);
    }

    @Test
    @DisplayName("sem vínculo é AcessoNegado, e a loja nem é buscada — nos dois")
    void autoriza_antes_de_buscar() {
        when(membros.buscarPorUsuarioELoja(AUTOR, LOJA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> minimos().definir(LOJA, AUTOR, Map.of()))
                .isInstanceOf(AcessoNegado.class);
        assertThatThrownBy(() -> descontos().definir(LOJA, AUTOR, BigDecimal.ONE))
                .isInstanceOf(AcessoNegado.class);
        verifyNoInteractions(estabelecimentos);
    }

    @Test
    @DisplayName("exige CONFIGURAR_LOJA: outra permissão qualquer não serve")
    void exige_configurar_loja() {
        when(membros.buscarPorUsuarioELoja(AUTOR, LOJA))
                .thenReturn(Optional.of(vinculoCom(Permissao.GERENCIAR_EQUIPE)));

        assertThatThrownBy(() -> minimos().definir(LOJA, AUTOR, Map.of()))
                .isInstanceOf(AcessoNegado.class);
        assertThatThrownBy(() -> descontos().definir(LOJA, AUTOR, BigDecimal.ONE))
                .isInstanceOf(AcessoNegado.class);
        verifyNoInteractions(estabelecimentos);
    }

    @Test
    @DisplayName("a M17 sai como OperacaoRecusada, com a mensagem do domínio na causa, e nada é gravado")
    void m17_traduzida() {
        when(membros.buscarPorUsuarioELoja(AUTOR, LOJA))
                .thenReturn(Optional.of(vinculoCom(Permissao.CONFIGURAR_LOJA)));
        when(estabelecimentos.buscarPorId(LOJA)).thenReturn(Optional.of(LojaDeTeste.pizzaria()));

        assertThatThrownBy(() -> minimos().definir(LOJA, AUTOR,
                Map.of(Modalidade.ENTREGA, new BigDecimal("10.00"))))
                .isInstanceOf(OperacaoRecusada.class)
                .satisfies(e -> assertThat(((OperacaoRecusada) e).motivo())
                        .isEqualTo(OperacaoRecusada.Motivo.PEDIDO_MINIMO))
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .cause().hasMessageContaining("M17");
        verify(estabelecimentos, never()).salvar(any());
    }

    @Test
    @DisplayName("a M15 sai como OperacaoRecusada, e nada é gravado")
    void m15_traduzida() {
        when(membros.buscarPorUsuarioELoja(AUTOR, LOJA))
                .thenReturn(Optional.of(vinculoCom(Permissao.CONFIGURAR_LOJA)));
        when(estabelecimentos.buscarPorId(LOJA)).thenReturn(Optional.of(LojaDeTeste.pizzaria()));

        assertThatThrownBy(() -> descontos().definir(LOJA, AUTOR, new BigDecimal("-1")))
                .isInstanceOf(OperacaoRecusada.class)
                .satisfies(e -> assertThat(((OperacaoRecusada) e).motivo())
                        .isEqualTo(OperacaoRecusada.Motivo.DESCONTO_NEGATIVO))
                .hasCauseInstanceOf(IllegalArgumentException.class);
        verify(estabelecimentos, never()).salvar(any());
    }

    @Test
    @DisplayName("com CONFIGURAR_LOJA, grava e devolve a operação como ficou")
    void grava() {
        when(membros.buscarPorUsuarioELoja(AUTOR, LOJA))
                .thenReturn(Optional.of(vinculoCom(Permissao.CONFIGURAR_LOJA)));
        when(estabelecimentos.buscarPorId(LOJA)).thenReturn(Optional.of(LojaDeTeste.pizzaria()));
        when(estabelecimentos.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));

        var operacao = descontos().definir(LOJA, AUTOR, new BigDecimal("3"));

        assertThat(operacao.descontoDeRetirada().valor()).isEqualByComparingTo("3.00");
    }
}
