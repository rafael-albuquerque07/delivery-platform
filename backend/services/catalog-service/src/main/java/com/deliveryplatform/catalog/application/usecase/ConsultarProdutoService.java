package com.deliveryplatform.catalog.application.usecase;

import com.deliveryplatform.catalog.application.exception.AcessoNegado;
import com.deliveryplatform.catalog.application.exception.ProdutoNaoEncontrado;
import com.deliveryplatform.catalog.application.port.in.ConsultarProduto;
import com.deliveryplatform.catalog.application.port.out.AutorizacaoComercialPort;
import com.deliveryplatform.catalog.application.port.out.ContextoDeAcesso;
import com.deliveryplatform.catalog.application.port.out.PermissaoDoCatalogo;
import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.Produto;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lê um produto da loja, inteiro.
 *
 * <p><b>A ordem das três etapas é a decisão desta rodada</b>, e inverter duas delas
 * abre um buraco:
 *
 * <ol>
 *   <li><b>autoriza para a loja da URL</b> — quem não tem vínculo recebe 403 e
 *       <b>não descobre nada</b> sobre produto nenhum;</li>
 *   <li><b>busca o produto</b>;</li>
 *   <li><b>confere que ele é da loja autorizada</b> — se não for, 404, o mesmo de
 *       não existir.</li>
 * </ol>
 *
 * <p>Se a busca viesse antes da autorização, um chamador sem vínculo distinguiria
 * produto que existe de produto que não existe pelo tempo ou pela resposta — e isso
 * é um scanner de produtos com dois passos.
 *
 * <p>A marcação responde o mesmo 404 para a mesma situação desde a G-G (ADR-056).
 * Entre 07/10 e 09/10 ela respondia 403, e este javadoc registrava a divergência.
 */
@Service
public class ConsultarProdutoService implements ConsultarProduto {

    private final AutorizacaoComercialPort autorizacao;
    private final ProdutoRepositorio produtos;

    public ConsultarProdutoService(
            AutorizacaoComercialPort autorizacao, ProdutoRepositorio produtos) {
        this.autorizacao = autorizacao;
        this.produtos = produtos;
    }

    @Override
    public Produto consultar(UUID estabelecimentoId, UUID produtoId) {
        // 1 · a loja da URL é o que autoriza — invariante 9: o identificador que vem
        //     da URL nunca é confiado, ele é conferido.
        ContextoDeAcesso contexto = autorizacao
                .contexto(estabelecimentoId)
                .orElseThrow(AcessoNegado::new);
        if (!contexto.pode(PermissaoDoCatalogo.VER_PRODUTO)) {
            throw new AcessoNegado();
        }

        // 2 · e só então o produto.
        Produto produto = produtos.buscarPorId(produtoId).orElseThrow(ProdutoNaoEncontrado::new);

        // 3 · produto de outra loja é a mesma coisa que produto que não existe.
        if (!produto.getEstabelecimentoId().equals(estabelecimentoId)) {
            throw new ProdutoNaoEncontrado();
        }

        // Qualquer estado de publicação sai. O comerciante precisa ver o rascunho
        // para agir sobre ele, pelo mesmo motivo que a listagem mostra o que não
        // está vendável — e a resposta carrega `estadoDePublicacao` para a tela
        // saber o que está olhando.
        return produto;
    }
}
