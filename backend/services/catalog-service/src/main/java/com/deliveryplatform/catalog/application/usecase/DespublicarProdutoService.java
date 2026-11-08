package com.deliveryplatform.catalog.application.usecase;

import com.deliveryplatform.catalog.application.exception.AcessoNegado;
import com.deliveryplatform.catalog.application.exception.ProdutoNaoEncontrado;
import com.deliveryplatform.catalog.application.port.in.DespublicarProduto;
import com.deliveryplatform.catalog.application.port.out.AutorizacaoComercialPort;
import com.deliveryplatform.catalog.application.port.out.ContextoDeAcesso;
import com.deliveryplatform.catalog.application.port.out.PermissaoDoCatalogo;
import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.Produto;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * O produto sai do cardápio (ADR-078): {@code ATIVO → INATIVO}, a transição que o
 * {@code catalogo.md} §8 nomeia ao declarar o {@code ProdutoDespublicadoV1}. O
 * {@code Produto.inativar()} existe desde a G-B; <b>este é o primeiro chamador que não
 * é teste</b>.
 *
 * <p>A ordem é a da publicação, com outra permissão: {@code DESATIVAR_PRODUTO}, que o
 * {@code merchant} publica desde o primeiro enum e que até aqui não guardava rota
 * nenhuma. Busca o produto <b>desta</b> loja — de outra loja é o mesmo 404 de
 * inexistente (ADR-056) —, tira do ar, grava.
 *
 * <p><b>Tirar do ar o que já saiu não é erro</b>, pela mesma razão da publicação
 * repetida (ADR-065 §5): o agregado põe {@code INATIVO} de novo, e a rota responde 200.
 * O rascunho é recusado pelo agregado — nunca apareceu, não há o que tirar.
 *
 * <p><b>A disponibilidade não muda.</b> Sair do cardápio é decisão do comerciante, e o
 * que o dia fez com o produto fica como estava (ADR-078).
 *
 * <p><b>O evento não sai</b>, pelo mesmo motivo da publicação: o {@code catalog} não
 * tem outbox, e o consumidor não tem código.
 */
@Service
public class DespublicarProdutoService implements DespublicarProduto {

    private final AutorizacaoComercialPort autorizacao;
    private final ProdutoRepositorio produtos;

    public DespublicarProdutoService(AutorizacaoComercialPort autorizacao,
                                     ProdutoRepositorio produtos) {
        this.autorizacao = autorizacao;
        this.produtos = produtos;
    }

    @Override
    public Produto despublicar(UUID estabelecimentoId, UUID produtoId) {
        ContextoDeAcesso contexto = autorizacao
                .contexto(estabelecimentoId)
                .orElseThrow(AcessoNegado::new);
        if (!contexto.pode(PermissaoDoCatalogo.DESATIVAR_PRODUTO)) {
            throw new AcessoNegado();
        }

        Produto produto = produtos.buscarPorId(produtoId)
                .filter(encontrado -> encontrado.getEstabelecimentoId().equals(estabelecimentoId))
                .orElseThrow(ProdutoNaoEncontrado::new);

        produto.inativar();

        return produtos.salvar(produto);
    }
}
