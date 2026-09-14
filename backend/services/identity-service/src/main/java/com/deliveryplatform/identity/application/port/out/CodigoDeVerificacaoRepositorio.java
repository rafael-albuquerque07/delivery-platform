package com.deliveryplatform.identity.application.port.out;

import com.deliveryplatform.identity.domain.model.CodigoDeVerificacao;
import com.deliveryplatform.identity.domain.model.Telefone;

import java.util.Optional;

/**
 * Porta de saída para o código de verificação do cadastro (ADR-042).
 *
 * <p><b>Não há {@code salvar}.</b> A operação é {@link #substituir}, e o nome é
 * a regra: existe no máximo um código por telefone, e pedir outro apaga o
 * anterior. Um {@code salvar} que por dentro apaga seria uma semântica
 * escondida num nome que promete outra coisa — e a tabela, que tem
 * {@code UNIQUE (telefone)}, recusaria o segundo com um erro de banco em vez
 * de uma regra de domínio.
 */
public interface CodigoDeVerificacaoRepositorio {

    /** Grava o código, descartando o que existir para o mesmo telefone. */
    CodigoDeVerificacao substituir(CodigoDeVerificacao codigo);

    Optional<CodigoDeVerificacao> buscarPorTelefone(Telefone telefone);

    /** Chamado quando o código cumpre o que tinha para cumprir: o cadastro. */
    void removerDe(Telefone telefone);
}
