package com.deliveryplatform.merchant.domain.model;

/**
 * As permissões de H2.1, mais uma (`estabelecimento.md` §2).
 *
 * <p><b>Mora no {@code merchant} e não em {@code :value-types}</b>, e é a mesma
 * pergunta que o {@code diaOperacional} levantou: com <b>um</b> serviço
 * precisando do tipo, não há decisão de lugar — ele mora onde está o único
 * consumidor, e está certo por construção. O gatilho para mudar é o segundo
 * serviço que precise <i>nomear</i> uma permissão, e não o que apenas recebe a
 * resposta de sim-ou-não da {@code AutorizacaoComercialPort} (ADR-011) — esse
 * não conta, como o {@code catalog} não conta para o dia operacional.
 */
public enum Permissao {

    VER_PRODUTO,
    CRIAR_PRODUTO,
    ALTERAR_PRODUTO,
    DESATIVAR_PRODUTO,

    VER_PEDIDO,
    ALTERAR_STATUS,

    VER_VENDAS,
    VER_ENTREGA,

    GERENCIAR_EQUIPE,

    /**
     * Acréscimo ao PRD, marcado como tal no documento de domínio. Abrir turno,
     * registrar adiantamento e fechar jornada mexem em dinheiro e não cabem em
     * nenhuma permissão de H2.1: {@code VER_VENDAS} é leitura e
     * {@code VER_ENTREGA} é outra coisa.
     */
    GERENCIAR_JORNADA
}
