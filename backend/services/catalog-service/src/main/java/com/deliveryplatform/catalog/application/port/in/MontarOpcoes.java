package com.deliveryplatform.catalog.application.port.in;

import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.valuetypes.Money;

import java.util.UUID;

/**
 * O comerciante monta as perguntas do produto: o grupo, e as opções dentro dele
 * (ADR-077). As duas escritas devolvem o produto inteiro, com os ids que o
 * próximo passo vai citar.
 */
public interface MontarOpcoes {

    Produto acrescentarGrupo(UUID estabelecimentoId, UUID produtoId, NovoGrupo grupo);

    Produto acrescentarOpcao(UUID estabelecimentoId, UUID produtoId, UUID grupoId, NovaOpcao opcao);

    /** O que o corpo trouxe para o grupo, já na forma do domínio. */
    record NovoGrupo(String nome, int minEscolhas, int maxEscolhas, int ordem) {
    }

    /** O que o corpo trouxe para a opção. O acréscimo pode ser zero ou negativo. */
    record NovaOpcao(String nome, Money acrescimo, int ordem) {
    }
}
