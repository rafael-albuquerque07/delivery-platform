package com.deliveryplatform.catalog.integration;

import com.deliveryplatform.catalog.application.port.in.ReativarNoExpediente;
import com.deliveryplatform.catalog.infrastructure.messaging.LeitorDeExpedienteAlterado.EventoIlegivel;
import com.deliveryplatform.catalog.infrastructure.persistence.mapper.ProdutoMapper.DocumentoIlegivel;
import com.deliveryplatform.catalog.support.Infraestrutura;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A classificação de exceções, medida contando invocações.
 *
 * <p>A reativação é um dublê que estoura: o que se mede não é o efeito, é <b>quantas
 * vezes o contêiner entregou a mensagem antes de mandá-la para a fila morta</b>. O
 * contador é o próprio Mockito. A contagem é exata depois do {@code receive} da fila
 * morta: a retentativa é em memória, dentro do interceptor, e só depois de esgotada a
 * mensagem é rejeitada.
 *
 * <p>Os dois números são a ADR-026 e a emenda dela: <b>quatro</b> entregas para falha
 * transitória, <b>uma</b> para recusa definitiva — e a exceção chega ao classificador
 * embrulhada pelo contêiner, que é o que este teste prova que a exclusão alcança.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "delivery.consumo-de-eventos.fila=" + ConsumoComFalhaIT.FILA,
        "delivery.consumo-de-eventos.intervalo=20ms",
        "delivery.consumo-de-eventos.teto-do-intervalo=80ms"})
class ConsumoComFalhaIT extends Infraestrutura {

    static final String FILA = "catalog.expediente-alterado.teste-falha";
    private static final String FILA_MORTA = FILA + ".morta";

    @MockitoBean ReativarNoExpediente reativar;

    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;

    @BeforeEach
    void filasVazias() {
        admin.purgeQueue(FILA, true);
        admin.purgeQueue(FILA_MORTA, true);
        reset(reativar);
    }

    @Test
    @DisplayName("3 · falha transitória: quatro entregas — a primeira e três repetições — e depois a fila morta")
    void falha_transitoria_tem_quatro_entregas() {
        when(reativar.reativar(any(), any())).thenThrow(new IllegalStateException("Mongo ocupado"));

        publicarAbertura();

        assertThat(rabbit.receive(FILA_MORTA, 10_000)).isNotNull();
        verify(reativar, times(4)).reativar(any(UUID.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("2 · recusa definitiva (EventoIlegivel), mesmo embrulhada pelo contêiner: uma entrega só")
    void evento_ilegivel_tem_uma_entrega() {
        when(reativar.reativar(any(), any())).thenThrow(new EventoIlegivel("corpo fora do contrato"));

        publicarAbertura();

        assertThat(rabbit.receive(FILA_MORTA, 10_000)).isNotNull();
        verify(reativar, times(1)).reativar(any(UUID.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("documento corrompido (DocumentoIlegivel) também é definitivo: uma entrega só")
    void documento_ilegivel_tem_uma_entrega() {
        when(reativar.reativar(any(), any()))
                .thenThrow(new DocumentoIlegivel("o documento do produto x não reconstitui um Produto"));

        publicarAbertura();

        assertThat(rabbit.receive(FILA_MORTA, 10_000)).isNotNull();
        verify(reativar, times(1)).reativar(any(UUID.class), any(LocalDate.class));
    }

    private void publicarAbertura() {
        String corpo = """
                {"eventId":"%s","eventType":"ExpedienteAlterado","eventVersion":1,
                 "occurredAt":"2026-10-10T21:00:04Z","correlationId":"x",
                 "payload":{"estabelecimentoId":"%s","motivo":"ABERTURA_DE_EXPEDIENTE",
                            "expedienteDeReferencia":"2026-10-10"}}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID());
        rabbit.send("delivery.eventos", "merchant.expediente.alterado.v1", MessageBuilder
                .withBody(corpo.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build());
    }
}
