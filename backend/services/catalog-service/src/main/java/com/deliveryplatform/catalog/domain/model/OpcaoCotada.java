package com.deliveryplatform.catalog.domain.model;

import com.deliveryplatform.valuetypes.Money;
import java.util.UUID;

/** Uma opção escolhida, cotada: o grupo e a opção com nome, e o acréscimo do momento (ADR-018). */
public record OpcaoCotada(UUID opcaoId, String nomeDoGrupo, String nomeDaOpcao, Money acrescimo) {
}
