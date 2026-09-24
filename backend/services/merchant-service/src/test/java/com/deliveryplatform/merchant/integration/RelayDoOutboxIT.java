package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.application.usecase.GerenciarEquipeService;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.infrastructure.outbox.OutboxJpaEntity;
import com.deliveryplatform.merchant.infrastructure.outbox.OutboxSpringDataRepository;
import com.deliveryplatform.merchant.infrastructure.outbox.RelayDoOutbox;
import com.deliveryplatform.merchant.support.EquipeDeTeste;
import com.deliveryplatform.merchant.support.Infraestrutura;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O evento sai do banco e chega num broker de verdade.
 *
 * <p><b>Por que RabbitMQ de verdade e não um {@code RabbitTemplate} dublê.</b>
 * Pelo mesmo motivo que fez o {@code IdentityDeMentira} da C-A servir o JWKS num
 * HTTP de verdade em vez de injetar um {@code JwtDecoder} de teste: um dublê
 * provaria que o relay chama um método, e não provaria nada sobre a exchange
 * existir, o binding casar com a chave de rota, a mensagem ser durável, ou o
 * payload chegar legível do outro lado. Sem isto, a primeira vez que o
 * {@code merchant} publicaria de verdade seria em produção.
 *
 * <p>O agendador fica ligado — é a configuração real —, mas com o atraso inicial
 * em uma hora, para que quem publica no teste seja a chamada explícita e não uma
 * thread de fundo. Teste que compete com o próprio {@code @Scheduled} é teste
 * que passa noventa por cento das vezes.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "delivery.outbox.habilitado=true",
        "delivery.outbox.atraso-inicial-ms=3600000",
        "delivery.outbox.intervalo-ms=3600000"
})
class RelayDoOutboxIT extends Infraestrutura {

    private static final String FILA = "teste.escuta-vinculo";

    @Autowired GerenciarEquipeService equipes;
    @Autowired MembroRepositorio membros;
    @Autowired EstabelecimentoRepositorio lojas;
    @Autowired OutboxSpringDataRepository outbox;
    @Autowired RelayDoOutbox relay;
    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired TopicExchange eventosDoDelivery;
    @Autowired ObjectMapper json;

    private UUID loja;
    private UUID marli;
    private UUID bia;

    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @BeforeEach
    void umaFilaDeTesteEUmaPizzaria() {
        // Quem declara fila e binding é o CONSUMIDOR, e no teste o consumidor é o
        // teste. O merchant declara só a exchange — um produtor que declara a
        // fila de quem consome acopla os dois pelo broker, que é o inverso do
        // que a fila existe para fazer.
        // Durável e sem auto-delete. O RabbitMQ 4 recusa fila transitória não
        // exclusiva (transient_nonexcl_queues, erro 541). E o receive com prazo
        // usa um consumidor temporário: ao cancelá-lo, uma fila auto-delete some,
        // e o segundo receive do mesmo teste recebe 404. O purge abaixo limpa
        // entre os casos, e o broker morre com a execução.
        admin.declareQueue(new Queue(FILA));
        admin.declareBinding(BindingBuilder.bind(new Queue(FILA))
                .to(eventosDoDelivery)
                .with("merchant.vinculo.#"));
        admin.purgeQueue(FILA);

        outbox.deleteAll();
        loja = lojas.salvar(LojaDeTeste.pizzaria()).getId();
        marli = membros.salvar(Membro.fundador(UUID.randomUUID(), loja, agora())).getUsuarioId();
        bia = membros.salvar(Membro.colaborador(
                UUID.randomUUID(), loja, EquipeDeTeste.DA_BIA, agora())).getUsuarioId();
    }

    private Message receber() {
        return rabbit.receive(FILA, 5_000);
    }

    @Test
    void o_evento_gravado_chega_na_fila() throws Exception {
        equipes.suspender(loja, marli, bia);

        assertThat(relay.publicarPendentes()).isEqualTo(1);

        Message mensagem = receber();
        assertThat(mensagem).as("a exchange existe, o binding casa e a mensagem saiu").isNotNull();

        var corpo = json.readTree(new String(mensagem.getBody(), StandardCharsets.UTF_8));
        assertThat(corpo.get("eventType").asString()).isEqualTo("VinculoAlterado");
        assertThat(corpo.get("payload").get("usuarioId").asString()).isEqualTo(bia.toString());
        assertThat(corpo.get("payload").get("estado").asString()).isEqualTo("SUSPENSO");
    }

    @Test
    void a_mensagem_carrega_a_chave_de_idempotencia_no_cabecalho() {
        equipes.suspender(loja, marli, bia);
        relay.publicarPendentes();

        Message mensagem = receber();
        assertThat(mensagem).isNotNull();

        OutboxJpaEntity linha = outbox.findAll().getFirst();
        assertThat(mensagem.getMessageProperties().getMessageId())
                .as("deduplicar é trabalho de infraestrutura do consumidor, e "
                        + "infraestrutura não deveria precisar desserializar domínio")
                .isEqualTo(linha.getId().toString());
        assertThat(mensagem.getMessageProperties().getHeader("tipo").toString())
                .isEqualTo("VinculoAlterado");
    }

    @Test
    void a_mensagem_e_persistente() {
        equipes.suspender(loja, marli, bia);
        relay.publicarPendentes();

        assertThat(receber().getMessageProperties().getReceivedDeliveryMode())
                .as("um outbox entregando em fila volátil trocou a durabilidade do "
                        + "banco pela memória do broker")
                .isEqualTo(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
    }

    @Test
    void o_que_ja_saiu_nao_sai_de_novo() {
        equipes.suspender(loja, marli, bia);

        assertThat(relay.publicarPendentes()).isEqualTo(1);
        assertThat(relay.publicarPendentes())
                .as("a garantia é PELO MENOS uma vez — o que não pode é o relay "
                        + "reenviar em toda passada o histórico inteiro")
                .isZero();

        assertThat(receber()).isNotNull();
        assertThat(rabbit.receive(FILA, 500)).isNull();
    }

    @Test
    void a_linha_publicada_e_marcada_e_some_do_indice_de_pendentes() {
        equipes.suspender(loja, marli, bia);
        relay.publicarPendentes();

        assertThat(outbox.countByPublicadoEmIsNull()).isZero();
        assertThat(outbox.findAll().getFirst().getPublicadoEm()).isNotNull();
    }

    /**
     * O lote sai inteiro, e a ordem é a que o <b>contrato</b> promete: a do
     * {@code occurredAt}, não a de chegada.
     *
     * <p>A ordem de chegada não é garantida nem dentro de um lote: cada
     * {@code send} pega um canal do cache, e com confirmação de publicação dois
     * envios seguidos podem sair por canais diferentes, que o broker não ordena
     * entre si. Publicar o lote num canal só daria a ordem e cobraria caro — uma
     * mensagem que derrubasse o canal levaria as seguintes junto, rodada após
     * rodada. Por isso o consumidor ordena por {@code occurredAt} (ADR-043 §4), e
     * é isso que este teste confere.
     */
    @Test
    void um_lote_com_varias_operacoes_sai_inteiro_e_se_ordena_por_occurredAt() {
        equipes.suspender(loja, marli, bia);
        equipes.reativar(loja, marli, bia);
        equipes.promover(loja, marli, bia);

        assertThat(relay.publicarPendentes()).isEqualTo(3);

        List<JsonNode> recebidos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Message mensagem = receber();
            assertThat(mensagem).as("o lote sai inteiro").isNotNull();
            recebidos.add(json.readTree(new String(mensagem.getBody(), StandardCharsets.UTF_8)));
        }
        recebidos.sort(Comparator.comparing(
                (JsonNode envelope) -> Instant.parse(envelope.get("occurredAt").asString())));

        assertThat(recebidos)
                .extracting(envelope -> envelope.get("payload").get("estado").asString()
                        + "/" + envelope.get("payload").get("papel").asString())
                .as("ordenado por occurredAt, o lote conta a história na ordem em que ela aconteceu")
                .containsExactly("SUSPENSO/COLABORADOR", "ATIVO/COLABORADOR", "ATIVO/ADMINISTRADOR");
    }

    @Test
    void sem_nada_pendente_o_relay_nao_faz_nada() {
        assertThat(relay.publicarPendentes()).isZero();
    }
}
