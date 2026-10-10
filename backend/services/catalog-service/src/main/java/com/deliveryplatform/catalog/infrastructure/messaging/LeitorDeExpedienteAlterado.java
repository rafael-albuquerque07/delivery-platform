package com.deliveryplatform.catalog.infrastructure.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

/**
 * Lê o corpo do {@code ExpedienteAlteradoV1} e devolve a abertura, se for uma.
 *
 * <p><b>Função pura, de propósito.</b> É aqui que mora a decisão mais fácil de errar
 * do consumidor — o que fazer com cada forma de corpo — e ela é testável sem
 * RabbitMQ, sem Spring e sem Mongo.
 *
 * <p>O corpo é o envelope de {@code contracts/events/_envelope-v1.json}, com os dados em
 * {@code payload}: {@code estabelecimentoId}, {@code motivo} e
 * {@code expedienteDeReferencia} ({@code contracts/eventos.md}). O produtor tira o
 * instante do payload e o põe no envelope como {@code occurredAt}.
 *
 * <p><b>Duas saídas, e elas significam coisas opostas:</b>
 *
 * <ul>
 *   <li>{@link Optional#empty()} — <b>o corpo está bom e não é para mim.</b> Um
 *       {@code motivo} que não é abertura, inclusive um que este serviço não conhece.
 *       Confirma e segue. É a cláusula 4 do {@code eventos.md}, que cita a ADR-027 §2:
 *       valor novo em enum é incompatível por padrão <i>"salvo se todos os consumidores
 *       tratarem valor desconhecido como ignorar"</i> — aqui há um consumidor só, e ele
 *       trata;</li>
 *   <li>{@link EventoIlegivel} — <b>o corpo não é o que o contrato diz.</b> Não é JSON,
 *       falta campo, data que não é data. <b>Repetir não ajuda</b>, e é por isso que ela
 *       existe separada: a fábrica deste consumo a manda direto para a fila morta, sem
 *       as quatro tentativas da ADR-026.</li>
 * </ul>
 *
 * <p>Juntar as duas seria o defeito: um corpo corrompido devolvendo vazio seria
 * confirmado em silêncio, e a abertura daquela loja se perderia sem erro em lugar
 * nenhum.
 *
 * <p><b>Leitura por árvore, e não por classe.</b> Campo a mais no envelope ou no payload
 * não existe para este leitor — ele lê os três de que precisa e ignora o resto. Um
 * {@code motivo} novo que chegasse junto de campos novos continua sendo "não é para mim",
 * e não "ilegível" — que é o que a cláusula 4 exige.
 */
public final class LeitorDeExpedienteAlterado {

    /** O único valor de {@code MotivoDoExpediente} que o {@code merchant} produz hoje. */
    static final String ABERTURA = "ABERTURA_DE_EXPEDIENTE";

    /** Só {@code readTree}: a configuração do mapper não muda a árvore lida. */
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private LeitorDeExpedienteAlterado() {
    }

    /**
     * @param corpo o JSON como veio do broker
     * @return a abertura, ou vazio se o evento é de outro motivo
     * @throws EventoIlegivel se o corpo não corresponde ao contrato
     */
    public static Optional<AberturaDeExpediente> ler(String corpo) {
        JsonNode envelope;
        try {
            envelope = JSON.readTree(corpo == null ? "" : corpo);
        } catch (JacksonException naoEJson) {
            throw new EventoIlegivel("o corpo não é JSON", naoEJson);
        }
        if (envelope == null || !envelope.isObject()) {
            throw new EventoIlegivel("o corpo não é um envelope");
        }

        JsonNode payload = envelope.path("payload");
        if (!payload.isObject()) {
            throw new EventoIlegivel("envelope sem payload");
        }

        String motivo = textoObrigatorio(payload, "motivo");
        if (!ABERTURA.equals(motivo)) {
            return Optional.empty();
        }

        UUID loja;
        try {
            loja = UUID.fromString(textoObrigatorio(payload, "estabelecimentoId"));
        } catch (IllegalArgumentException naoEUuid) {
            throw new EventoIlegivel("estabelecimentoId não é UUID", naoEUuid);
        }

        LocalDate expediente;
        try {
            expediente = LocalDate.parse(textoObrigatorio(payload, "expedienteDeReferencia"));
        } catch (DateTimeParseException naoEData) {
            throw new EventoIlegivel("expedienteDeReferencia não é data", naoEData);
        }

        return Optional.of(new AberturaDeExpediente(loja, expediente));
    }

    private static String textoObrigatorio(JsonNode payload, String campo) {
        JsonNode valor = payload.path(campo);
        if (valor.isMissingNode() || valor.isNull() || !valor.isString()
                || valor.asString().isBlank()) {
            throw new EventoIlegivel("payload sem " + campo);
        }
        return valor.asString();
    }

    /** O que a reativação precisa, e nada mais. */
    public record AberturaDeExpediente(UUID estabelecimentoId, LocalDate expediente) {
    }

    /**
     * Corpo que não corresponde ao contrato.
     *
     * <p><b>Não é falha transitória</b>: a fábrica deste consumo a classifica como
     * definitiva, e a mensagem vai para a fila morta na primeira entrega. Quatro
     * tentativas de ler o mesmo JSON inválido são quatro leituras do mesmo JSON inválido.
     */
    public static class EventoIlegivel extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public EventoIlegivel(String motivo) {
            super(motivo);
        }

        public EventoIlegivel(String motivo, Throwable causa) {
            super(motivo, causa);
        }
    }
}
