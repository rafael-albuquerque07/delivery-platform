package com.deliveryplatform.catalog.application.usecase;

import com.deliveryplatform.catalog.application.port.in.ReativarNoExpediente.Resultado;
import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.Produto;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Um produto, uma transação. <b>Bean separado de propósito</b>, e o motivo não é
 * organização.
 *
 * <p>A repetição depois de um conflito de versão (ADR-052) tem de abrir uma
 * transação <b>nova</b>: repetir dentro de uma transação que já falhou não repete
 * nada, porque ela está condenada. Então o laço de repetição mora no
 * {@link ReativarNoExpedienteService} e chama este método daqui.
 *
 * <p><b>E tem de ser outro bean.</b> Se o laço e o {@code @Transactional}
 * estivessem na mesma classe, a chamada seria autoinvocação: ela não passa pelo
 * proxy do Spring, a anotação é <b>silenciosamente ignorada</b>, e cada repetição
 * rodaria sem transação nenhuma. O teste não pegaria — ele passaria, com o
 * comportamento errado. É o mesmo gênero de armadilha do {@code @Primary} da G-B4:
 * configuração que muda o significado do código sem mudar uma linha dele.
 */
@Component
public class ReativacaoDeUmProduto {

    private final ProdutoRepositorio produtos;

    public ReativacaoDeUmProduto(ProdutoRepositorio produtos) {
        this.produtos = produtos;
    }

    /**
     * Relê, decide no agregado e grava. <b>A releitura é o ponto:</b> a consulta de
     * candidatos devolveu identificadores, e o documento pode ter mudado entre a
     * consulta e agora. A versão lida aqui é a versão que a gravação vai conferir.
     *
     * <p>Produto que desapareceu entre a consulta e a releitura não é erro: é uma
     * despublicação concorrente, e ela ganha.
     */
    @Transactional
    public Resultado reativarUm(UUID produtoId, LocalDate expedienteQueAbriu) {
        Optional<Produto> achado = produtos.buscarPorId(produtoId);
        if (achado.isEmpty()) {
            return Resultado.NADA;
        }
        Produto produto = achado.get();

        // O predicado é do domínio, e ele pode discordar da consulta. Quando
        // discorda, nada muda e o número sai no relatório em vez de sumir.
        if (!produto.reativarNoExpediente(expedienteQueAbriu)) {
            return new Resultado(0, 0, 1);
        }

        produtos.salvar(produto);
        return new Resultado(1, 0, 0);
    }
}
