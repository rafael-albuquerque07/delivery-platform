package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.infrastructure.messaging.LeitorDeExpedienteAlterado;
import com.deliveryplatform.catalog.infrastructure.messaging.LeitorDeExpedienteAlterado.AberturaDeExpediente;
import com.deliveryplatform.catalog.infrastructure.messaging.LeitorDeExpedienteAlterado.EventoIlegivel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A fronteira entre "não é para mim" e "alguém olhe", sem RabbitMQ e sem Spring.
 *
 * <p>Os corpos seguem o envelope do {@code contracts/eventos.md}: os dados em
 * {@code payload}, com {@code estabelecimentoId}, {@code motivo} e
 * {@code expedienteDeReferencia}. Hoje o {@code MotivoDoExpediente} do {@code merchant}
 * tem um valor só, {@code ABERTURA_DE_EXPEDIENTE} — por isso os casos 1 e 2 usam valores
 * que ele ainda não produz.
 */
class LeitorDeExpedienteAlteradoTest {

    private static final UUID LOJA = UUID.fromString("1c9c1f2e-3b44-4a71-9f2a-6b0d5e8c4a10");

    private static String envelope(String payload) {
        return """
                {"eventId":"9a3c7f10-2b58-4d6e-b1c4-5e0f8a2d3b71","eventType":"ExpedienteAlterado",
                 "eventVersion":1,"occurredAt":"2026-09-26T21:00:04.117293Z",
                 "correlationId":"9a3c7f10-2b58-4d6e-b1c4-5e0f8a2d3b71","payload":%s}"""
                .formatted(payload);
    }

    private static String abertura(String loja, String expediente) {
        return envelope("""
                {"estabelecimentoId":"%s","motivo":"ABERTURA_DE_EXPEDIENTE","expedienteDeReferencia":"%s"}"""
                .formatted(loja, expediente));
    }

    // ── vazio: corpo bom, não é para mim ────────────────────────────────────

    @Test
    @DisplayName("1 · motivo que não é abertura (fechamento, quando existir) → vazio")
    void fechamento_e_vazio() {
        String corpo = envelope("""
                {"estabelecimentoId":"%s","motivo":"FECHAMENTO_DE_EXPEDIENTE","expedienteDeReferencia":"2026-09-26"}"""
                .formatted(LOJA));

        assertThat(LeitorDeExpedienteAlterado.ler(corpo)).isEmpty();
    }

    @Test
    @DisplayName("2 · motivo desconhecido, com campos novos junto, → vazio e não exceção (cláusula 4)")
    void motivo_desconhecido_com_campos_novos_e_vazio() {
        // A medição que a cláusula 4 do eventos.md pede: um motivo novo chega junto de
        // campos novos, no payload e no envelope. A leitura por árvore os ignora.
        String corpo = """
                {"eventId":"x","eventType":"ExpedienteAlterado","eventVersion":2,"campoNovo":true,
                 "payload":{"estabelecimentoId":"%s","motivo":"PAUSA_PROGRAMADA",
                            "expedienteDeReferencia":"2026-09-26","duracaoEmMinutos":30}}"""
                .formatted(LOJA);

        assertThat(LeitorDeExpedienteAlterado.ler(corpo)).isEmpty();
    }

    @Test
    @DisplayName("3 · abertura de outra loja, bem formada → presente (o leitor não filtra loja)")
    void abertura_de_qualquer_loja_e_presente() {
        UUID outra = UUID.randomUUID();

        Optional<AberturaDeExpediente> lida =
                LeitorDeExpedienteAlterado.ler(abertura(outra.toString(), "2026-09-26"));

        assertThat(lida).contains(new AberturaDeExpediente(outra, LocalDate.parse("2026-09-26")));
    }

    @Test
    @DisplayName("abertura com campo a mais no payload → presente")
    void abertura_com_campo_a_mais_e_presente() {
        String corpo = envelope("""
                {"estabelecimentoId":"%s","motivo":"ABERTURA_DE_EXPEDIENTE",
                 "expedienteDeReferencia":"2026-09-26","ocorridoEm":"2026-09-26T21:00:04Z"}"""
                .formatted(LOJA));

        assertThat(LeitorDeExpedienteAlterado.ler(corpo)).isPresent();
    }

    // ── EventoIlegivel: o corpo não é o contrato ────────────────────────────

    @Test
    @DisplayName("4 · não é JSON → ilegível")
    void nao_e_json() {
        assertThatThrownBy(() -> LeitorDeExpedienteAlterado.ler("<html>502</html>"))
                .isInstanceOf(EventoIlegivel.class);
    }

    @Test
    @DisplayName("5 · JSON válido e vazio → ilegível")
    void json_vazio() {
        assertThatThrownBy(() -> LeitorDeExpedienteAlterado.ler("{}"))
                .isInstanceOf(EventoIlegivel.class);
    }

    @Test
    @DisplayName("6 · abertura sem expedienteDeReferencia → ilegível")
    void sem_expediente() {
        String corpo = envelope("""
                {"estabelecimentoId":"%s","motivo":"ABERTURA_DE_EXPEDIENTE"}""".formatted(LOJA));

        assertThatThrownBy(() -> LeitorDeExpedienteAlterado.ler(corpo))
                .isInstanceOf(EventoIlegivel.class)
                .hasMessageContaining("expedienteDeReferencia");
    }

    @Test
    @DisplayName("7 · abertura sem estabelecimentoId → ilegível")
    void sem_loja() {
        String corpo = envelope("""
                {"motivo":"ABERTURA_DE_EXPEDIENTE","expedienteDeReferencia":"2026-09-26"}""");

        assertThatThrownBy(() -> LeitorDeExpedienteAlterado.ler(corpo))
                .isInstanceOf(EventoIlegivel.class)
                .hasMessageContaining("estabelecimentoId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ontem", "2026-13-45"})
    @DisplayName("8 · expedienteDeReferencia que não é data → ilegível")
    void expediente_que_nao_e_data(String valor) {
        assertThatThrownBy(() -> LeitorDeExpedienteAlterado.ler(abertura(LOJA.toString(), valor)))
                .isInstanceOf(EventoIlegivel.class);
    }

    @Test
    @DisplayName("9 · motivo ausente → ilegível, e não evento de outro tipo")
    void motivo_ausente() {
        // O contrato traz motivo em todo evento, e o produtor sempre o preenche.
        String corpo = envelope("""
                {"estabelecimentoId":"%s","expedienteDeReferencia":"2026-09-26"}""".formatted(LOJA));

        assertThatThrownBy(() -> LeitorDeExpedienteAlterado.ler(corpo))
                .isInstanceOf(EventoIlegivel.class)
                .hasMessageContaining("motivo");
    }

    @Test
    @DisplayName("loja que não é UUID → ilegível")
    void loja_que_nao_e_uuid() {
        assertThatThrownBy(() -> LeitorDeExpedienteAlterado.ler(abertura("loja-1", "2026-09-26")))
                .isInstanceOf(EventoIlegivel.class);
    }
}
