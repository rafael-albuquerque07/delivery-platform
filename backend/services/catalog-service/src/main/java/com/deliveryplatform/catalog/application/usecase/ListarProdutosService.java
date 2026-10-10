package com.deliveryplatform.catalog.application.usecase;

import com.deliveryplatform.catalog.application.exception.AcessoNegado;
import com.deliveryplatform.catalog.application.port.in.ListarProdutos;
import com.deliveryplatform.catalog.application.port.out.AutorizacaoComercialPort;
import com.deliveryplatform.catalog.application.port.out.ContextoDeAcesso;
import com.deliveryplatform.catalog.application.port.out.PermissaoDoCatalogo;
import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Duas linhas de autorização e uma de consulta — e as duas primeiras são a
 * razão de esta rodada existir.
 *
 * <p><b>1. A autorização não é local.</b> O {@code merchant} é a fonte da
 * verdade do vínculo (ADR-043), e este serviço pergunta. É a primeira vez que
 * um serviço deste repositório depende de outro para responder uma requisição —
 * e o que torna isso possível sem credencial de serviço é o token de quem pediu,
 * encaminhado (ADR-045).
 *
 * <p><b>2. A permissão é conferida sobre o contexto, não sobre o token.</b>
 * Mesmo argumento do {@code merchant}: o token carrega seis claims e nenhum
 * papel. Uma permissão revogada há dez segundos já não vale aqui — enquanto
 * dentro do token ela valeria até expirar.
 *
 * <p><b>3. Falha fechada, e sem ramo que perdoe.</b> Sem contexto, nega. Sem
 * {@code VER_PRODUTO}, nega. E se o {@code merchant} não responder, a
 * {@code AutorizacaoIndisponivel} sobe e vira a mesma recusa — não existe
 * caminho neste método que devolva produto quando a autorização não foi obtida.
 *
 * <p><b>O que este método não faz:</b> não filtra por vendável. O
 * {@code vendavel} é derivado (§4 do {@code catalogo.md}) e não é campo, então
 * quem quiser só os vendáveis filtra o que voltou — e o cardápio do comerciante
 * precisa ver justamente o que <i>não</i> está vendável, para agir.
 */
@Service
public class ListarProdutosService implements ListarProdutos {

    private final AutorizacaoComercialPort autorizacao;
    private final ProdutoRepositorio produtos;

    public ListarProdutosService(AutorizacaoComercialPort autorizacao,
                                 ProdutoRepositorio produtos) {
        this.autorizacao = autorizacao;
        this.produtos = produtos;
    }

    @Override
    public Page<Produto> publicadosDaLoja(UUID estabelecimentoId, Pageable paginacao) {
        ContextoDeAcesso contexto = autorizacao
                .contexto(estabelecimentoId)
                .orElseThrow(AcessoNegado::new);

        if (!contexto.pode(PermissaoDoCatalogo.VER_PRODUTO)) {
            throw new AcessoNegado();
        }

        return produtos.publicadosDe(estabelecimentoId, paginacao);
    }
}
