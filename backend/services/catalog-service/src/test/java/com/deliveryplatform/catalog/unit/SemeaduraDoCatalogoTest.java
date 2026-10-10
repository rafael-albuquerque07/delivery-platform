package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.config.SemeaduraProperties;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.infrastructure.semeadura.SemeaduraDoCatalogo;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * O produto da fixture (ADR-059): publicado, esgotado com carimbo velho, e com
 * uma opção esgotada também — os dois caminhos da reativação.
 */
@ExtendWith(MockitoExtension.class)
class SemeaduraDoCatalogoTest {

    private static final UUID LOJA = UUID.fromString("5eed0000-0000-4000-8000-000000000001");
    private static final Instant AGORA = Instant.parse("2026-10-10T05:30:00Z");

    @Mock
    ProdutoRepositorio produtos;

    @Test
    void desligada_nao_toca_no_repositorio() {
        semeadura(false).run(new DefaultApplicationArguments());

        verifyNoInteractions(produtos);
    }

    @Test
    void grava_um_produto_publicado_e_esgotado_que_a_proxima_abertura_reativa() {
        when(produtos.publicadosDe(eq(LOJA), any())).thenReturn(Page.empty());
        when(produtos.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));

        semeadura(true).run(new DefaultApplicationArguments());

        ArgumentCaptor<Produto> gravado = ArgumentCaptor.forClass(Produto.class);
        verify(produtos).salvar(gravado.capture());
        Produto produto = gravado.getValue();

        assertThat(produto.getEstabelecimentoId()).isEqualTo(LOJA);
        assertThat(produto.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.ATIVO);
        assertThat(produto.getDisponibilidade().estado()).isEqualTo(EstadoDeDisponibilidade.ESGOTADO_HOJE);
        assertThat(produto.getDisponibilidade().expedienteDeReferencia()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(produto.vendavel()).isFalse();

        List<Opcao> opcoes = produto.getGruposDeOpcoes().get(0).opcoes();
        assertThat(opcoes).extracting(o -> o.disponibilidade().estado()).containsExactly(
                EstadoDeDisponibilidade.DISPONIVEL,
                EstadoDeDisponibilidade.DISPONIVEL,
                EstadoDeDisponibilidade.ESGOTADO_HOJE);

        // Trinta dias atrás é menor que o expediente que abrir, em qualquer fuso.
        LocalDate ontemEmQualquerLugar = LocalDate.of(2026, 10, 9);
        assertThat(produto.getDisponibilidade().deveReativarNoExpediente(ontemEmQualquerLugar)).isTrue();
        assertThat(opcoes.get(2).disponibilidade().deveReativarNoExpediente(ontemEmQualquerLugar)).isTrue();
    }

    @Test
    void loja_que_ja_tem_produto_nao_ganha_outro() {
        when(produtos.publicadosDe(eq(LOJA), any()))
                .thenReturn(new PageImpl<>(List.of(ProdutoDeTeste.margheritaPublicada())));

        semeadura(true).run(new DefaultApplicationArguments());

        verify(produtos, never()).salvar(any());
    }

    private SemeaduraDoCatalogo semeadura(boolean ligada) {
        return new SemeaduraDoCatalogo(
                new SemeaduraProperties(ligada), produtos, Clock.fixed(AGORA, ZoneOffset.UTC));
    }
}
