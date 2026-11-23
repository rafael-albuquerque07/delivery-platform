package com.deliveryplatform.merchant.domain.model;

/**
 * As permissões de H2.1, mais três (`estabelecimento.md` §2).
 *
 * <p><b>Mora no {@code merchant} e não em {@code :value-types}</b>, e é a mesma
 * pergunta que o {@code diaOperacional} levantou: com <b>um</b> serviço
 * precisando do tipo, não há decisão de lugar — ele mora onde está o único
 * consumidor, e está certo por construção. O gatilho para mudar é o segundo
 * serviço que precise <i>nomear</i> uma permissão, e não o que apenas recebe a
 * resposta de sim-ou-não da {@code AutorizacaoComercialPort} (ADR-011) — esse
 * não conta, como o {@code catalog} não conta para o dia operacional.
 *
 * <p><b>O gatilho disparou na G-B3, e a resposta foi não.</b> A porta acabou
 * não devolvendo sim-ou-não: devolve a lista de permissões (ADR-045), e o
 * {@code catalog-service} passou a nomear as quatro de produto, com o próprio
 * enum no recorte que usa. Mesma palavra, recortes diferentes — o mesmo
 * argumento com que a ADR-040 recusou o {@code Telefone}. O que está entre os
 * dois serviços é o texto, no corpo da resposta HTTP, e o consumidor descarta o
 * que não conhece. Ver a emenda de 28/09/2026 à ADR-040.
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
    GERENCIAR_JORNADA,

    /**
     * A configuração da loja inteira — o horário de funcionamento desde a E-4, e o que
     * mais da H1.2 ganhar rota (ADR-073 §2). Ampla de propósito: ninguém pediu "pode
     * mexer no horário mas não nas taxas". O fundador a recebe na criação, por
     * {@code EnumSet.allOf}, e a migration {@code V9} a deu aos fundadores anteriores a ela; o convite
     * não a oferece na tela.
     */
    CONFIGURAR_LOJA,

    /**
     * Lançar no painel o pedido que chegou por telefone ou no balcão (H4.5): <i>"uma permissão
     * própria, que nenhuma das permissões de H2.1 cobre"</i>. O fundador a recebe com as outras, e
     * o convite a oferece — é para o dono dá-la ao atendente. Nenhuma rota a exige hoje; a rota de
     * lançar é a da fatia 4 da ADR-086, no {@code order}.
     */
    LANCAR_PEDIDO
}
