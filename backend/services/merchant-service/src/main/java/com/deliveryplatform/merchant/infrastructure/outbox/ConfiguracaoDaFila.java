package com.deliveryplatform.merchant.infrastructure.outbox;

import com.deliveryplatform.merchant.config.OutboxProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * O que o {@code merchant} declara no broker: <b>a exchange, e nada mais</b>.
 *
 * <p>Fila e binding são de quem consome. Um produtor que declara a fila do
 * consumidor acopla os dois pelo broker — o inverso exato do que a fila existe
 * para fazer — e cria a dúvida de quem é o dono quando os dois declararem com
 * argumentos diferentes.
 *
 * <p>É {@code topic} para que o consumidor escolha o recorte:
 * {@code merchant.vinculo.#} assina todas as versões do vínculo,
 * {@code merchant.#} assina tudo do serviço. O {@code merchant} publica em
 * {@code merchant.vinculo.alterado.v1} e não precisa saber quem escuta.
 *
 * <p>Durável: sobrevive ao restart do broker. Um outbox entregando numa exchange
 * volátil teria trocado a durabilidade do banco pela memória do RabbitMQ.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class ConfiguracaoDaFila {

    @Bean
    public TopicExchange eventosDoDelivery(OutboxProperties propriedades) {
        return new TopicExchange(propriedades.exchange(), true, false);
    }
}
