package com.deliveryplatform.catalog.infrastructure.messaging;

import com.deliveryplatform.catalog.config.ConsumoDeEventosProperties;
import com.deliveryplatform.catalog.infrastructure.messaging.LeitorDeExpedienteAlterado.EventoIlegivel;
import com.deliveryplatform.catalog.infrastructure.persistence.mapper.ProdutoMapper.DocumentoIlegivel;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;

/**
 * A primeira fila durável e a primeira fila morta deste repositório.
 *
 * <p><b>Por que esta fila não é uma {@code AnonymousQueue}, como a da invalidação.</b> A
 * invalidação pode perder um evento sem consequência: ela esvazia um cache que se
 * reenche na próxima leitura. A reativação não pode. O evento perdido ali é <b>a loja
 * que abriu</b>, e o preço é o catálogo dela ficar indisponível o dia inteiro, sem erro
 * em lugar nenhum. Então a fila é <b>durável, nomeada e compartilhada</b>: ela existe
 * quando o {@code catalog} está fora do ar, e acumula. Com duas instâncias, cada abertura
 * é processada por uma só — o contrário da invalidação, e de propósito.
 *
 * <p><b>Por que existe uma segunda fábrica de contêineres.</b> A política da ADR-026 —
 * quatro tentativas, 1 s, 4 s, 16 s — vale <b>para este ouvinte</b>. No
 * {@code spring.rabbitmq.listener.simple.retry} ela valeria para a <b>fábrica
 * inteira</b>, e o {@code OuvinteDeVinculoAlterado} passaria a retentar algo que ele
 * trata confirmando sempre. A fábrica separa os dois contratos.
 *
 * <p><b>E este ouvinte não confirma à mão.</b> A confirmação é do contêiner
 * ({@link AcknowledgeMode#AUTO}): a exceção <b>tem</b> de escapar do método, porque é ela
 * que aciona a retentativa e, no fim, a fila morta.
 *
 * <p><b>A retentativa é a do Spring Framework 7, não a do {@code spring-retry}.</b> O
 * pacote desta rodada foi escrito contra o {@code spring-retry}, que <b>não está no
 * classpath</b>: o Spring AMQP 4.1.1 recebe um {@code org.springframework.core.retry.RetryPolicy}
 * (medido no jar, G-C3b). As recusas definitivas são <b>excluídas</b> da política, e o
 * {@code ConsumoDeExpedienteIT} conta as invocações para provar que a exclusão alcança a
 * exceção mesmo embrulhada pelo contêiner.
 */
@Configuration
@EnableConfigurationProperties(ConsumoDeEventosProperties.class)
public class ConfiguracaoDoConsumoDeExpediente {

    static final String TROCA_MORTA = "catalog.morta";

    /** A chave do produtor ({@code ExpedienteAlteradoV1.chaveDeRota()} e {@code eventos.md}). */
    static final String CHAVE = "merchant.expediente.alterado.v1";

    static final String FABRICA = "fabricaComFilaMorta";

    private final ConsumoDeEventosProperties propriedades;

    public ConfiguracaoDoConsumoDeExpediente(ConsumoDeEventosProperties propriedades) {
        this.propriedades = propriedades;
    }

    /**
     * A fila de trabalho, com a troca morta pendurada nela.
     *
     * <p>O {@code x-dead-letter-exchange} é argumento da fila, e <b>argumento de fila é
     * imutável</b>: se ela já existir no broker sem ele, a declaração falha com
     * {@code PRECONDITION_FAILED} e o serviço não sobe. Apague a fila e suba de novo —
     * não mude o nome para contornar.
     */
    @Bean
    Queue filaDeExpedienteAlterado() {
        return QueueBuilder.durable(propriedades.fila())
                .deadLetterExchange(TROCA_MORTA)
                .deadLetterRoutingKey(propriedades.filaMorta())
                .build();
    }

    @Bean
    Queue filaMortaDeExpedienteAlterado() {
        return QueueBuilder.durable(propriedades.filaMorta()).build();
    }

    @Bean
    DirectExchange trocaMorta() {
        return new DirectExchange(TROCA_MORTA, true, false);
    }

    @Bean
    Binding ligacaoDaFilaMorta() {
        return BindingBuilder.bind(filaMortaDeExpedienteAlterado()).to(trocaMorta()).with(propriedades.filaMorta());
    }

    /**
     * A ligação da fila de trabalho à troca de eventos.
     *
     * <p>A troca vem do único bean {@link TopicExchange} do serviço, o
     * {@code eventosDoDelivery} da {@code ConfiguracaoDaEscuta}, injetado por tipo. <b>Não
     * declare a troca de novo aqui</b>: duas declarações com argumentos diferentes fazem o
     * serviço não subir.
     */
    @Bean
    Binding ligacaoDeExpedienteAlterado(TopicExchange eventosDoDelivery) {
        return BindingBuilder.bind(filaDeExpedienteAlterado()).to(eventosDoDelivery).with(CHAVE);
    }

    /** A fábrica deste ouvinte, e só dele. */
    @Bean(FABRICA)
    SimpleRabbitListenerContainerFactory fabricaComFilaMorta(ConnectionFactory conexao) {
        SimpleRabbitListenerContainerFactory fabrica = new SimpleRabbitListenerContainerFactory();
        fabrica.setConnectionFactory(conexao);
        fabrica.setAcknowledgeMode(AcknowledgeMode.AUTO);
        fabrica.setConcurrentConsumers(1);
        fabrica.setMaxConcurrentConsumers(1);
        // Rejeitar sem devolver à fila: é o que faz a mensagem cair na troca morta. Com
        // `true`, a recusa voltaria para a mesma fila — o laço apertado que a ADR-026 proíbe.
        fabrica.setDefaultRequeueRejected(false);
        fabrica.setAdviceChain(retentativaDaAdr026(propriedades));
        return fabrica;
    }

    /**
     * ADR-026: quatro tentativas — a primeira entrega e três repetições —, 1 s, 4 s, 16 s,
     * e depois a fila morta. Recusa definitiva vai na primeira (emenda de 10/10/2026).
     */
    static MethodInterceptor retentativaDaAdr026(ConsumoDeEventosProperties propriedades) {
        RetryPolicy politica = RetryPolicy.builder()
                .maxRetries(propriedades.tentativas() - 1L)
                .delay(propriedades.intervalo())
                .multiplier(propriedades.multiplicador())
                .maxDelay(propriedades.tetoDoIntervalo())
                .excludes(EventoIlegivel.class, DocumentoIlegivel.class)
                .build();

        return RetryInterceptorBuilder.stateless()
                .retryPolicy(politica)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }
}
