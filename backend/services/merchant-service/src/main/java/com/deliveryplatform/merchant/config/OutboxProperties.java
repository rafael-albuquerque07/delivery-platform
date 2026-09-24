package com.deliveryplatform.merchant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * As quatro perguntas do relay, com resposta em configuração.
 *
 * <p>Nenhuma delas é segredo — nome de exchange, tamanho de lote e intervalo não
 * são credencial. O que é credencial mora em {@code spring.rabbitmq.*}, vem de
 * variável de ambiente, e não aparece aqui nem no {@code .env.example} com
 * valor.
 */
@ConfigurationProperties(prefix = "delivery.outbox")
public record OutboxProperties(

        /** Topic exchange para onde os eventos do merchant saem. */
        @DefaultValue("delivery.eventos") String exchange,

        /**
         * Quantas linhas por lote.
         *
         * <p>É também o tamanho da janela de reenvio: se o processo morrer no
         * meio de um lote já publicado e não commitado, até esta quantidade de
         * mensagens sai duas vezes. Lote grande diminui o número de transações e
         * aumenta essa janela.
         */
        @DefaultValue("100") int tamanhoDoLote,

        /** Liga e desliga o agendador. Desligado nos testes que não publicam. */
        @DefaultValue("true") boolean habilitado
) {
}
