package com.deliveryplatform.merchant.infrastructure.outbox;

import com.deliveryplatform.merchant.application.port.out.Outbox;
import com.deliveryplatform.merchant.domain.evento.EventoDeDominio;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * Grava o evento na tabela, na transação de quem chamou, <b>já no envelope que
 * vai para a fila</b>.
 *
 * <p><b>O formato é o envelope que o repositório já tinha</b>
 * ({@code contracts/events/_envelope-v1.json}): {@code eventId},
 * {@code eventType}, {@code eventVersion}, {@code occurredAt},
 * {@code correlationId} e {@code payload}. Campos de envelope em inglês, porque
 * são infraestrutura (ADR-035); o {@code payload} em português, porque é
 * domínio. A versão vive em {@code eventVersion} e nunca dentro do
 * {@code eventType} ({@code contracts/README.md}).
 *
 * <p><b>Onde nasce o {@code eventId}.</b> Aqui, e uma vez só: ele é a chave
 * primária da linha. O relay pode reenviar a mesma linha quantas vezes precisar
 * sem inventar identificador novo, e é isso que torna praticável a cláusula do
 * contrato que manda o consumidor ser idempotente por {@code eventId}.
 */
@Component
public class OutboxJpa implements Outbox {

    private final OutboxSpringDataRepository linhas;
    private final ObjectMapper json;

    public OutboxJpa(OutboxSpringDataRepository linhas, ObjectMapper json) {
        this.linhas = linhas;
        this.json = json;
    }

    @Override
    public void registrar(EventoDeDominio evento) {
        UUID eventId = UUID.randomUUID();

        // O instante vai para o envelope como occurredAt. Deixá-lo também no
        // payload seriam dois campos dizendo a mesma coisa e obrigados a
        // concordar para sempre.
        ObjectNode payload = json.valueToTree(evento);
        payload.remove("ocorridoEm");

        ObjectNode envelope = json.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", evento.tipo());
        envelope.put("eventVersion", evento.versao());
        envelope.put("occurredAt", evento.ocorridoEm().toString());
        // Sem correlação de requisição no serviço ainda, o evento é a raiz da
        // própria cadeia: quem reagir a ele propaga este valor adiante.
        envelope.put("correlationId", eventId.toString());
        envelope.set("payload", payload);

        linhas.save(new OutboxJpaEntity(
                eventId,
                evento.tipo(),
                evento.versao(),
                evento.agregado(),
                evento.agregadoId(),
                evento.chaveDeRota(),
                envelope.toString(),
                evento.ocorridoEm()));
    }
}
