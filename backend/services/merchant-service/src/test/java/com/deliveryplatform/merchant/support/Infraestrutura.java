package com.deliveryplatform.merchant.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Os contêineres que o {@code merchant-service} precisa, <b>subidos uma vez para
 * a execução inteira</b>.
 *
 * <p><b>Por que isto passou a existir na C-B.</b> Até a C-A o RabbitMQ não era
 * usado por ninguém, mas o {@code spring-boot-starter-amqp} já chegava pelo
 * {@code delivery.messaging-conventions}; o Actuator registrava o indicador de
 * saúde por causa do starter, o indicador não achava broker nenhum, e o
 * {@code /actuator/health} respondia {@code 503} no teste. A saída
 * foi desligar o indicador. A partir da ADR-043 o broker é dependência de
 * verdade, e desligar o indicador deixaria o serviço se declarar são sem
 * conseguir publicar um único evento — a pior forma de indisponibilidade, a que
 * o orquestrador não vê.
 *
 * <p><b>Por que campo estático com {@code start()} no bloco estático, e não
 * {@code @Container}.</b> {@code @Container} amarra o ciclo de vida à classe de
 * teste: cada IT subiria o seu RabbitMQ. Aqui os dois contêineres sobem no
 * primeiro que precisar e ficam de pé até o fim da JVM — o Ryuk derruba. É o
 * padrão singleton do Testcontainers, e é a diferença entre somar alguns
 * segundos à execução e somar alguns segundos <i>por classe de teste</i>.
 *
 * <p>Estender esta classe é o jeito de um IT dizer "eu preciso de infraestrutura
 * de verdade". Quem não estende não sobe nada.
 */
public abstract class Infraestrutura {

    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    protected static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:4-alpine");

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void apontarParaOsConteineres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // addresses, e não host/port: o application.yml define
        // spring.rabbitmq.addresses, e com ela definida o Spring Boot ignora
        // host e port — o teste falaria com localhost:5672 em vez do contêiner.
        registry.add("spring.rabbitmq.addresses",
                () -> RABBIT.getHost() + ":" + RABBIT.getAmqpPort());
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }
}
