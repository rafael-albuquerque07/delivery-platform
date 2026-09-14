package com.deliveryplatform.merchant.application.port.out;

import com.deliveryplatform.merchant.domain.model.Equipe;
import com.deliveryplatform.merchant.domain.model.Membro;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída do vínculo. O adaptador mora em
 * {@code infrastructure/persistence}, como o do {@code Estabelecimento}.
 *
 * <p><b>Duas leituras, e elas existem por motivos opostos.</b>
 * {@link #buscarPorUsuarioELoja} é o caminho quente — é a consulta que toda
 * requisição de todo serviço vai fazer para resolver permissão (ADR-011), e
 * carrega um vínculo só. {@link #equipeParaAlteracao} é o caminho frio: carrega
 * a loja inteira e <b>toma um cadeado</b>, porque é o único jeito de A3 valer
 * sob concorrência.
 *
 * <p>Os nomes dizem para que servem justamente para que ninguém use o caro no
 * lugar do barato — e, pior, o barato no lugar do caro.
 */
public interface MembroRepositorio {

    Membro salvar(Membro membro);

    /** O caminho quente da autorização contextual. Sem cadeado, sem a equipe. */
    Optional<Membro> buscarPorUsuarioELoja(UUID usuarioId, UUID estabelecimentoId);

    /**
     * Toma o cadeado na linha do estabelecimento e devolve a equipe inteira.
     *
     * <p><b>Precisa estar dentro de uma transação</b>, e a do chamador é a que
     * vale: um cadeado que é solto antes da escrita não protege nada. Duas
     * alterações de equipe da mesma loja passam a se enfileirar; de lojas
     * diferentes, não se veem.
     */
    Equipe equipeParaAlteracao(UUID estabelecimentoId);
}
