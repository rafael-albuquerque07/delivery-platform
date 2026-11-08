package com.deliveryplatform.catalog.application.usecase;

import com.deliveryplatform.catalog.application.exception.AcessoNegado;
import com.deliveryplatform.catalog.application.exception.ProdutoNaoEncontrado;
import com.deliveryplatform.catalog.application.port.in.MontarOpcoes;
import com.deliveryplatform.catalog.application.port.out.AutorizacaoComercialPort;
import com.deliveryplatform.catalog.application.port.out.ContextoDeAcesso;
import com.deliveryplatform.catalog.application.port.out.PermissaoDoCatalogo;
import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.Produto;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * O grupo de opções e a opção ganham rota de escrita (ADR-077). O modelo já estava
 * escrito desde a G-B — {@code GrupoDeOpcoes}, {@code Opcao}, C3 a C5 —, e o que
 * faltava era quem o chamasse.
 *
 * <p>A ordem é a da publicação: permissão ({@code ALTERAR_PRODUTO} — montar as opções
 * é alterar o produto), busca o produto <b>desta</b> loja — de outra loja é o mesmo 404
 * de inexistente (ADR-056) —, altera pela raiz, grava. As regras são do agregado: C3
 * sempre, e as da publicação quando o produto já está no cardápio.
 *
 * <p><b>Nenhum evento sai</b>, pelo mesmo motivo da publicação: o {@code catalog} não
 * tem outbox, e o consumidor não tem código.
 */
@Service
public class MontarOpcoesService implements MontarOpcoes {

    private final AutorizacaoComercialPort autorizacao;
    private final ProdutoRepositorio produtos;

    public MontarOpcoesService(AutorizacaoComercialPort autorizacao,
                               ProdutoRepositorio produtos) {
        this.autorizacao = autorizacao;
        this.produtos = produtos;
    }

    @Override
    public Produto acrescentarGrupo(UUID estabelecimentoId, UUID produtoId, NovoGrupo grupo) {
        return alterar(estabelecimentoId, produtoId, produto -> produto.criarGrupo(
                grupo.nome(), grupo.minEscolhas(), grupo.maxEscolhas(), grupo.ordem()));
    }

    @Override
    public Produto acrescentarOpcao(UUID estabelecimentoId, UUID produtoId, UUID grupoId,
                                    NovaOpcao opcao) {
        return alterar(estabelecimentoId, produtoId, produto -> produto.criarOpcao(
                grupoId, opcao.nome(), opcao.acrescimo(), opcao.ordem()));
    }

    private Produto alterar(UUID estabelecimentoId, UUID produtoId, Consumer<Produto> mudanca) {
        ContextoDeAcesso contexto = autorizacao
                .contexto(estabelecimentoId)
                .orElseThrow(AcessoNegado::new);
        if (!contexto.pode(PermissaoDoCatalogo.ALTERAR_PRODUTO)) {
            throw new AcessoNegado();
        }

        Produto produto = produtos.buscarPorId(produtoId)
                .filter(encontrado -> encontrado.getEstabelecimentoId().equals(estabelecimentoId))
                .orElseThrow(ProdutoNaoEncontrado::new);

        mudanca.accept(produto);

        return produtos.salvar(produto);
    }
}
