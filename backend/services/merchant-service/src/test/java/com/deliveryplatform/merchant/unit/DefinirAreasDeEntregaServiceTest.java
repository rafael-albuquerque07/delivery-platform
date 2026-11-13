package com.deliveryplatform.merchant.unit;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import com.deliveryplatform.merchant.application.exception.AreasRecusadas;
import com.deliveryplatform.merchant.application.port.in.DefinirAreasDeEntrega.AreaPedida;
import com.deliveryplatform.merchant.application.port.in.DefinirAreasDeEntrega.FaixaPedida;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.application.usecase.DefinirAreasDeEntregaService;
import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.Membro;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * O caso de uso das áreas (ADR-081): autoriza antes de buscar, exige {@code CONFIGURAR_LOJA}, e
 * traduz as recusas das três construções num ponto só — sem conferir regra nenhuma.
 */
@ExtendWith(MockitoExtension.class)
class DefinirAreasDeEntregaServiceTest {

    private static final UUID LOJA = UUID.randomUUID();
    private static final UUID AUTOR = UUID.randomUUID();

    @Mock MembroRepositorio membros;
    @Mock EstabelecimentoRepositorio estabelecimentos;

    private DefinirAreasDeEntregaService servico() {
        return new DefinirAreasDeEntregaService(membros, estabelecimentos);
    }

    private static Membro vinculoCom(Permissao permissao) {
        return Membro.reconstituir(UUID.randomUUID(), AUTOR, LOJA, Papel.COLABORADOR,
                EnumSet.of(permissao), EstadoDoMembro.ATIVO, EquipeDeTeste.AGORA, EquipeDeTeste.AGORA);
    }

    private static AreaPedida area(String nome, String taxa, FaixaPedida... faixas) {
        return new AreaPedida(nome, new BigDecimal(taxa), true, List.of(faixas));
    }

    private void comPermissao() {
        when(membros.buscarPorUsuarioELoja(AUTOR, LOJA))
                .thenReturn(Optional.of(vinculoCom(Permissao.CONFIGURAR_LOJA)));
        lenient().when(estabelecimentos.buscarPorId(LOJA)).thenReturn(Optional.of(LojaDeTeste.pizzaria()));
    }

    @Test
    @DisplayName("sem vínculo, ou sem CONFIGURAR_LOJA, é AcessoNegado, e a loja nem é buscada")
    void autoriza_antes_de_buscar() {
        when(membros.buscarPorUsuarioELoja(AUTOR, LOJA)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servico().definir(LOJA, AUTOR, List.of()))
                .isInstanceOf(AcessoNegado.class);

        when(membros.buscarPorUsuarioELoja(AUTOR, LOJA))
                .thenReturn(Optional.of(vinculoCom(Permissao.GERENCIAR_EQUIPE)));
        assertThatThrownBy(() -> servico().definir(LOJA, AUTOR, List.of()))
                .isInstanceOf(AcessoNegado.class);
        verifyNoInteractions(estabelecimentos);
    }

    @Test
    @DisplayName("as três construções saem como AreasRecusadas, cada uma com o seu motivo, e nada é gravado")
    void tres_recusas_tres_motivos() {
        comPermissao();

        assertThatThrownBy(() -> servico().definir(LOJA, AUTOR,
                List.of(area("Pina", "5", new FaixaPedida("123", "51000-000")))))
                .isInstanceOf(AreasRecusadas.class)
                .satisfies(e -> assertThat(((AreasRecusadas) e).motivo())
                        .isEqualTo(AreasRecusadas.Motivo.CEP_INVALIDO));
        assertThatThrownBy(() -> servico().definir(LOJA, AUTOR, List.of(area("Pina", "-1"))))
                .isInstanceOf(AreasRecusadas.class)
                .satisfies(e -> assertThat(((AreasRecusadas) e).motivo())
                        .isEqualTo(AreasRecusadas.Motivo.AREA_INVALIDA));
        assertThatThrownBy(() -> servico().definir(LOJA, AUTOR,
                List.of(area("Pina", "5"), area("PINA", "6"))))
                .isInstanceOf(AreasRecusadas.class)
                .satisfies(e -> assertThat(((AreasRecusadas) e).motivo())
                        .isEqualTo(AreasRecusadas.Motivo.AREAS_EM_CONFLITO))
                .cause().hasMessageContaining("M9");
        verify(estabelecimentos, never()).salvar(any());
    }

    @Test
    @DisplayName("com CONFIGURAR_LOJA, grava e devolve as áreas como ficaram")
    void grava() {
        comPermissao();
        when(estabelecimentos.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));

        var areas = servico().definir(LOJA, AUTOR,
                List.of(area("Afogados", "4", new FaixaPedida("50750-000", "50770-999"))));

        assertThat(areas).singleElement().satisfies(a -> {
            assertThat(a.nome()).isEqualTo("Afogados");
            assertThat(a.faixasDeCep()).extracting(f -> f.inicio()).containsExactly("50750000");
        });
    }
}
