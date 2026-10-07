package com.deliveryplatform.catalog.api.dto;

import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * O produto inteiro, com os grupos e as opções — o que o resumo da listagem não
 * carrega.
 *
 * <p><b>Ele existe por um motivo concreto:</b> a rota de marcar a disponibilidade de
 * uma opção (G-C2) precisa de {@code grupoId} e {@code opcaoId}, e <b>não havia como
 * um cliente descobri-los</b>. A W-C mediu isso e deixou a tela da opção de fora.
 *
 * <p><b>A forma dos campos que o {@link ProdutoResumoResponse} também tem é a dele</b>:
 * {@code precoBase} e {@code acrescimo} como {@code BigDecimal}, e
 * {@code disponibilidade} como o próprio enum — o estado, sem o carimbo. O carimbo é
 * do servidor e não é da tela; duas representações do mesmo dado na mesma API é como
 * as duas divergem, e o front já lê uma.
 *
 * <p><b>O que ele deliberadamente não traz:</b> o {@code precoMinimoPossivel} (C2).
 * A tela da opção mostra o acréscimo de cada opção, que já vem nos grupos.
 * <b>Gatilho escrito:</b> a primeira tela que mostre "a partir de".
 */
public record ProdutoResponse(
        UUID id,
        UUID categoriaId,
        String nome,
        String descricao,
        BigDecimal precoBase,
        int ordem,
        ModoDeControle modoDeControle,
        EstadoDePublicacao estadoDePublicacao,
        EstadoDeDisponibilidade disponibilidade,
        boolean vendavel,
        List<GrupoResponse> gruposDeOpcoes) {

    public static ProdutoResponse de(Produto produto) {
        return new ProdutoResponse(
                produto.getId(),
                produto.getCategoriaId(),
                produto.getNome(),
                produto.getDescricao(),
                produto.getPrecoBase().valor(),
                produto.getOrdem(),
                produto.getModoDeControle(),
                produto.getEstadoDePublicacao(),
                produto.getDisponibilidade().estado(),
                produto.vendavel(),
                produto.getGruposDeOpcoes().stream().map(GrupoResponse::de).toList());
    }

    /** O grupo, com as opções na ordem em que o comerciante as pôs. */
    public record GrupoResponse(
            UUID id,
            String nome,
            int minEscolhas,
            int maxEscolhas,
            int ordem,
            List<OpcaoResponse> opcoes) {

        static GrupoResponse de(GrupoDeOpcoes grupo) {
            return new GrupoResponse(grupo.id(), grupo.nome(), grupo.minEscolhas(),
                    grupo.maxEscolhas(), grupo.ordem(),
                    grupo.opcoes().stream().map(OpcaoResponse::de).toList());
        }
    }

    /**
     * A opção. <b>O {@code id} é o que a rota de marcação exige</b>, e é a razão de
     * esta rodada existir.
     */
    public record OpcaoResponse(
            UUID id,
            String nome,
            BigDecimal acrescimo,
            int ordem,
            EstadoDeDisponibilidade disponibilidade) {

        static OpcaoResponse de(Opcao opcao) {
            return new OpcaoResponse(opcao.id(), opcao.nome(), opcao.acrescimo().valor(),
                    opcao.ordem(), opcao.disponibilidade().estado());
        }
    }
}
