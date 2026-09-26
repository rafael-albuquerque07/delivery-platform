package com.deliveryplatform.merchant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * O interruptor da varredura de abertura (ADR-046). O intervalo e o atraso
 * inicial são lidos direto pelo {@code @Scheduled}, com os mesmos nomes.
 *
 * <p>Nenhuma é segredo, e por isso moram aqui e não em variável de ambiente
 * com nome de credencial.
 */
@ConfigurationProperties(prefix = "delivery.expediente")
public record ExpedienteProperties(

        /**
         * Liga e desliga a varredura.
         *
         * <p>Desligada nos testes que não são sobre ela. Um {@code @Scheduled}
         * rodando por baixo de um teste que não o espera é a origem mais comum
         * de teste que passa noventa por cento das vezes — custou uma execução
         * inteira na C-B.
         */
        @DefaultValue("true") boolean varreduraHabilitada
) {
}
