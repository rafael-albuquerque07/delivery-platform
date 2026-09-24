package com.deliveryplatform.merchant.infrastructure.outbox;

import com.deliveryplatform.merchant.config.OutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Tira do outbox o que ainda não saiu e publica.
 *
 * <p><b>O que ele garante e o que ele não garante.</b> Garante que nada que foi
 * gravado deixa de ser publicado: enquanto {@code publicado_em} for nulo, a
 * linha volta no próximo lote. Não garante que nada seja publicado duas vezes —
 * publicar no broker e marcar a linha não são atômicos entre si, e se a
 * publicação vai e o commit não, a mensagem sai de novo. A entrega é <b>pelo
 * menos uma vez</b>, está escrita assim na ADR-043 §3, e o contrato obriga o
 * consumidor a ser idempotente por {@code eventoId}.
 *
 * <p><b>A falha de um evento não segura a fila.</b> Cada linha é publicada e
 * marcada dentro do laço; uma que estoure tem o motivo registrado e as outras do
 * lote seguem. Sem isso, uma mensagem malformada bloquearia todas as posteriores
 * — que é o modo de falha clássico de outbox e o mais difícil de diagnosticar,
 * porque o sintoma aparece num evento que não tem defeito nenhum.
 *
 * <p>Desligável por propriedade. Os testes que não têm nada a ver com publicação
 * desligam o relay em vez de conviver com um agendador rodando por baixo — um
 * {@code @Scheduled} ativo é a fonte mais comum de teste que passa noventa por
 * cento das vezes.
 */
@Component
@ConditionalOnProperty(name = "delivery.outbox.habilitado", havingValue = "true", matchIfMissing = true)
public class RelayDoOutbox {

    private static final Logger log = LoggerFactory.getLogger(RelayDoOutbox.class);

    private final OutboxSpringDataRepository linhas;
    private final RabbitTemplate rabbit;
    private final OutboxProperties propriedades;
    private final Clock relogio;

    public RelayDoOutbox(OutboxSpringDataRepository linhas,
                         RabbitTemplate rabbit,
                         OutboxProperties propriedades,
                         Clock relogio) {
        this.linhas = linhas;
        this.rabbit = rabbit;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    /**
     * @return quantas linhas foram publicadas neste lote — só para o teste poder
     *         afirmar o efeito sem dormir esperando o agendador.
     */
    @Scheduled(
            fixedDelayString = "${delivery.outbox.intervalo-ms:1000}",
            initialDelayString = "${delivery.outbox.atraso-inicial-ms:2000}")
    @Transactional
    public int publicarPendentes() {
        List<OutboxJpaEntity> lote = linhas.travarLotePendente(propriedades.tamanhoDoLote());
        if (lote.isEmpty()) {
            return 0;
        }

        Instant agora = Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);
        int publicadas = 0;

        for (OutboxJpaEntity linha : lote) {
            try {
                rabbit.send(propriedades.exchange(), linha.getChaveDeRota(), mensagem(linha));
                linha.publicado(agora);
                publicadas++;
            } catch (RuntimeException e) {
                // Sem payload no log: a tabela outbox é cópia durável do evento e
                // vale para ela a mesma regra do log. O que fica é o suficiente
                // para achar a linha: tipo, id e o motivo.
                linha.falhou(e.getClass().getSimpleName() + ": " + e.getMessage());
                log.warn("outbox: falha ao publicar {} id={} tentativa={}",
                        linha.getTipo(), linha.getId(), linha.getTentativas());
            }
        }
        return publicadas;
    }

    private Message mensagem(OutboxJpaEntity linha) {
        return MessageBuilder
                .withBody(linha.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setContentEncoding(StandardCharsets.UTF_8.name())
                // PERSISTENT: o broker grava antes de confirmar. Um outbox que
                // entrega para uma fila volátil trocou a durabilidade do banco
                // pela memória do broker, e passa a garantir o que o banco já
                // garantia sem ele.
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                // O consumidor decide o que fazer sem abrir o corpo, e um
                // roteador pode descartar versão que não conhece.
                .setHeader("tipo", linha.getTipo())
                .setHeader("versao", linha.getVersao())
                // Chave de idempotência também no cabeçalho, além do payload:
                // deduplicar é responsabilidade de infraestrutura do consumidor,
                // e infraestrutura não deveria precisar desserializar domínio.
                .setMessageId(linha.getId().toString())
                .setTimestamp(java.util.Date.from(linha.getOcorridoEm()))
                .build();
    }
}
