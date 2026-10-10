package com.deliveryplatform.catalog.integration;

import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.Disponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.Infraestrutura;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * O consumo do {@code ExpedienteAlteradoV1} de ponta a ponta: publicado na troca do
 * {@code merchant}, lido pelo ouvinte, reativado no Mongo — ou na fila morta.
 *
 * <p><b>A prova é o estado, não a configuração.</b> Nenhum caso afirma que a fila tem
 * {@code x-dead-letter-exchange} ou que a política tem quatro tentativas: isso passaria
 * com a mensagem nunca chegando à fila morta. O que se afirma é a mensagem tirada da
 * fila morta com um {@code receive}, e o produto lido do banco.
 *
 * <p><b>Fila própria.</b> O contexto do Spring fica em cache entre as classes de teste,
 * e cada contexto liga o ouvinte; com o nome padrão, todos disputariam a mesma fila.
 * Esta classe usa uma fila só dela. Os outros contextos continuam recebendo os mesmos
 * eventos pela fila padrão e reativando no mesmo Mongo — o que não muda nenhuma
 * asserção daqui, porque reativar duas vezes é a idempotência que o caso 5 prova.
 *
 * <p><b>"Nada aconteceu" sem esperar por tempo.</b> A fábrica tem um consumidor só, então
 * as mensagens são processadas em ordem: publica-se o caso, depois uma abertura-sentinela
 * de outra loja, e quando a sentinela reativa, o caso já foi processado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "delivery.consumo-de-eventos.fila=" + ConsumoDeExpedienteIT.FILA,
        "delivery.consumo-de-eventos.intervalo=20ms",
        "delivery.consumo-de-eventos.teto-do-intervalo=80ms"})
class ConsumoDeExpedienteIT extends Infraestrutura {

    static final String FILA = "catalog.expediente-alterado.teste-consumo";
    private static final String FILA_MORTA = FILA + ".morta";
    private static final String CHAVE = "merchant.expediente.alterado.v1";

    private static final LocalDate ONTEM = LocalDate.parse("2026-10-09");
    private static final LocalDate HOJE = LocalDate.parse("2026-10-10");

    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired ProdutoRepositorio produtos;
    @Autowired MongoTemplate mongo;

    @BeforeEach
    void filasEColecaoVazias() {
        admin.purgeQueue(FILA, true);
        admin.purgeQueue(FILA_MORTA, true);
        mongo.getCollection("produtos").deleteMany(new Document());
    }

    @Test
    @DisplayName("1 · abertura bem formada: o produto de ontem volta, e a fila morta fica vazia")
    void abertura_reativa() {
        Produto esgotado = produtos.salvar(ProdutoDeTeste.esgotadoHojeEm(ONTEM));

        publicar(abertura(ProdutoDeTeste.LOJA, HOJE));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(estado(esgotado)).isEqualTo(EstadoDeDisponibilidade.DISPONIVEL));
        assertThat(rabbit.receive(FILA_MORTA, 200)).isNull();
    }

    @Test
    @DisplayName("2 · corpo malformado vai para a fila morta, e nada muda no banco")
    void corpo_malformado_vai_para_a_fila_morta() {
        Produto esgotado = produtos.salvar(ProdutoDeTeste.esgotadoHojeEm(ONTEM));

        publicar("<html>502</html>");

        Message morta = rabbit.receive(FILA_MORTA, 10_000);
        assertThat(morta).isNotNull();
        assertThat(new String(morta.getBody(), StandardCharsets.UTF_8)).isEqualTo("<html>502</html>");
        assertThat(estado(esgotado)).isEqualTo(EstadoDeDisponibilidade.ESGOTADO_HOJE);
    }

    @Test
    @DisplayName("4 · motivo que não é abertura: nada reativado, e a fila morta vazia — ignorar não é fila morta")
    void outro_motivo_e_ignorado() {
        Produto esgotado = produtos.salvar(ProdutoDeTeste.esgotadoHojeEm(ONTEM));
        Produto sentinela = sentinelaEsgotadaOntem();

        publicar(envelope(ProdutoDeTeste.LOJA, "FECHAMENTO_DE_EXPEDIENTE", HOJE));
        publicar(abertura(sentinela.getEstabelecimentoId(), HOJE));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(estado(sentinela)).isEqualTo(EstadoDeDisponibilidade.DISPONIVEL));
        assertThat(estado(esgotado)).isEqualTo(EstadoDeDisponibilidade.ESGOTADO_HOJE);
        assertThat(rabbit.receive(FILA_MORTA, 200)).isNull();
    }

    /**
     * A prova que a ADR-057 exige de si mesma: a exceção à invariante 7 não é uma
     * afirmação sobre o código, é este teste. A segunda entrega não acha nada atrás do
     * expediente, e o documento nem é regravado — a versão não muda.
     */
    @Test
    @DisplayName("5 · a mesma abertura entregue duas vezes deixa o mesmo estado")
    void reentrega_e_idempotente() {
        Produto esgotado = produtos.salvar(ProdutoDeTeste.esgotadoHojeEm(ONTEM));
        String mensagem = abertura(ProdutoDeTeste.LOJA, HOJE);

        publicar(mensagem);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(estado(esgotado)).isEqualTo(EstadoDeDisponibilidade.DISPONIVEL));
        Long versaoDepoisDaPrimeira = relido(esgotado).getVersao();

        Produto sentinela = sentinelaEsgotadaOntem();
        publicar(mensagem);
        publicar(abertura(sentinela.getEstabelecimentoId(), HOJE));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(estado(sentinela)).isEqualTo(EstadoDeDisponibilidade.DISPONIVEL));

        Produto depoisDaSegunda = relido(esgotado);
        assertThat(depoisDaSegunda.getDisponibilidade().estado()).isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);
        assertThat(depoisDaSegunda.getVersao()).isEqualTo(versaoDepoisDaPrimeira);
        assertThat(rabbit.receive(FILA_MORTA, 200)).isNull();
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    private Produto sentinelaEsgotadaOntem() {
        Produto p = Produto.rascunho(UUID.randomUUID(), ProdutoDeTeste.CATEGORIA, "Sentinela",
                Precos.reais("10.00"), ModoDeControle.QUALITATIVO, 0);
        p.marcar(Disponibilidade.esgotadoHoje(Instant.parse("2026-10-09T22:00:00Z"), ONTEM));
        return produtos.salvar(p);
    }

    private Produto relido(Produto p) {
        return produtos.buscarPorId(p.getId()).orElseThrow();
    }

    private EstadoDeDisponibilidade estado(Produto p) {
        return relido(p).getDisponibilidade().estado();
    }

    private static String abertura(UUID loja, LocalDate expediente) {
        return envelope(loja, "ABERTURA_DE_EXPEDIENTE", expediente);
    }

    private static String envelope(UUID loja, String motivo, LocalDate expediente) {
        return """
                {"eventId":"%s","eventType":"ExpedienteAlterado","eventVersion":1,
                 "occurredAt":"2026-10-10T21:00:04Z","correlationId":"%s",
                 "payload":{"estabelecimentoId":"%s","motivo":"%s","expedienteDeReferencia":"%s"}}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), loja, motivo, expediente);
    }

    private void publicar(String corpo) {
        rabbit.send("delivery.eventos", CHAVE, MessageBuilder
                .withBody(corpo.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build());
    }
}
