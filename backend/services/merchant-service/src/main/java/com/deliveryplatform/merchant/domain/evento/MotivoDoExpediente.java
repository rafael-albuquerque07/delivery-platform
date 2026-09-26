package com.deliveryplatform.merchant.domain.evento;

/**
 * Por que o expediente mudou.
 *
 * <p><b>Um valor, e é de propósito.</b> O {@code estabelecimento.md} §7
 * descreve o evento como "abriu, fechou, pausou, retomou". Os outros três são
 * atos de verdade, com dono óbvio, e nenhum deles é produzido hoje — um valor
 * de enum sem emissor é uma promessa com sintaxe de código. É a mesma razão que
 * tirou {@code CONVIDADO} do {@code EstadoDoMembro} na B1 e {@code EXPIRADO} do
 * {@code EstadoDoConvite} na B2.
 *
 * <p>Acrescentar valor a enum é mudança compatível (ADR-027), e o contrato já
 * obriga o consumidor a tolerar valor desconhecido. O custo de esperar é zero;
 * o de antecipar é uma lista que mente.
 */
public enum MotivoDoExpediente {

    /**
     * A loja entrou no horário de funcionamento e o expediente daquele dia
     * operacional ainda não tinha sido publicado.
     *
     * <p>É o único motivo que faz o {@code catalog} reativar
     * {@code ESGOTADO_HOJE} ({@code catalogo.md} §3). Retomada de pausa não
     * reativa nada — e é por isso que pausa e abertura precisam ser
     * distinguíveis aqui.
     */
    ABERTURA_DE_EXPEDIENTE
}
