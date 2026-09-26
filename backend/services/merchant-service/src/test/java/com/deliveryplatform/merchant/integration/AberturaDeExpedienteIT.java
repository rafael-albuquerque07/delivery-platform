package com.deliveryplatform.merchant.integration;

import com.deliveryplatform.merchant.application.port.out.AberturaDeExpedienteRepositorio;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.Outbox;
import com.deliveryplatform.merchant.application.usecase.PublicarAberturaDeExpediente;
import com.deliveryplatform.merchant.infrastructure.outbox.OutboxJpaEntity;
import com.deliveryplatform.merchant.infrastructure.outbox.OutboxSpringDataRepository;
import com.deliveryplatform.merchant.support.Infraestrutura;
import com.deliveryplatform.merchant.support.LojaDeTeste;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A abertura do expediente, do relógio até a linha de outbox.
 *
 * <p>O teste do meio — {@code a_padaria_que_abre_duas_vezes_publica_uma_vez} —
 * é a asserção que a ADR-025 §5 pede com um exemplo e que nunca teve quem a
 * escrevesse. E ele prova a emenda desta rodada: a ADR dizia que o catálogo
 * receberia <b>dois</b> eventos de abertura naquele dia; com a marca d'água ele
 * recebe <b>um</b>.
 *
 * <p>O relógio é injetado, então nada aqui dorme. O agendador fica desligado:
 * quem chama a passada é o teste.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "delivery.outbox.habilitado=false",
        "delivery.expediente.varredura-habilitada=false"
})
class AberturaDeExpedienteIT extends Infraestrutura {

    @Autowired EstabelecimentoRepositorio lojas;
    @Autowired OutboxSpringDataRepository outbox;
    @Autowired ObjectMapper json;

    @Autowired AberturaDeExpedienteRepositorio aberturas;
    @Autowired Outbox porta;
    @Autowired TransactionTemplate transacao;
    @PersistenceContext EntityManager em;

    /**
     * O caso de uso é montado à mão, com o relógio parado no instante que cada
     * caso pede. Injetar o bean obrigaria a mexer no relógio da aplicação
     * inteira — e nenhum teste aqui dorme.
     *
     * <p><b>A transação é aberta aqui, e não pelo {@code @Transactional} do caso
     * de uso.</b> Um objeto criado com {@code new} não passa pelo proxy do
     * Spring, e o {@code insert … on conflict do nothing} recusa rodar sem
     * transação ({@code TransactionRequiredException}). É a mesma transação que
     * o bean abriria em produção: marca d'água e outbox commitam juntos.
     */
    private int passadaEm(Instant instante) {
        var caso = new PublicarAberturaDeExpediente(
                lojas, aberturas, porta, Clock.fixed(instante, ZoneOffset.UTC));
        return transacao.execute(status -> caso.umaPassada());
    }

    /**
     * A varredura percorre <b>todas</b> as lojas, e o banco é compartilhado entre
     * as classes ({@code support.Infraestrutura}): o {@code EquipeControllerIT},
     * o {@code SaidaConcorrenteIT} e os do outbox commitam pizzarias com o mesmo
     * horário. Sem esvaziar a tabela, a contagem de uma passada seria a soma do
     * que as outras classes deixaram — é a regra do {@code CLAUDE.md} para
     * asserção sobre a tabela inteira. O {@code cascade} leva junto a marca
     * d'água, os membros e os convites.
     */
    @BeforeEach
    void limpar() {
        outbox.deleteAll();
        transacao.executeWithoutResult(status ->
                em.createNativeQuery("truncate table estabelecimento cascade").executeUpdate());
    }

    private UUID pizzariaDas18As02() {
        // LojaDeTeste.pizzaria() nasce com terça 18:00–02:00 — a faixa que cruza
        // a meia-noite, exemplo do estabelecimento.md §4 — e fuso America/Sao_Paulo.
        // Os instantes do fim do arquivo foram escolhidos para ela.
        return lojas.salvar(LojaDeTeste.pizzaria()).getId();
    }

    private JsonNode payloadDaUnicaLinha() {
        List<OutboxJpaEntity> linhas = outbox.findAll();
        assertThat(linhas).hasSize(1);
        return json.readTree(linhas.getFirst().getPayload()).get("payload");
    }

    // ── o caminho ───────────────────────────────────────────────────────────

    @Test
    void dentro_do_horario_a_abertura_e_publicada() {
        UUID loja = pizzariaDas18As02();

        assertThat(passadaEm(TERCA_AS_19H)).isEqualTo(1);

        JsonNode payload = payloadDaUnicaLinha();
        assertThat(payload.get("estabelecimentoId").asString()).isEqualTo(loja.toString());
        assertThat(payload.get("motivo").asString()).isEqualTo("ABERTURA_DE_EXPEDIENTE");
    }

    @Test
    void fora_do_horario_nao_publica_nada() {
        pizzariaDas18As02();

        assertThat(passadaEm(TERCA_AS_15H)).isZero();
        assertThat(outbox.findAll()).isEmpty();
    }

    @Test
    @DisplayName("a padaria que abre duas vezes no mesmo dia publica uma vez — ADR-025 §5")
    void a_padaria_que_abre_duas_vezes_publica_uma_vez() {
        pizzariaDas18As02();

        assertThat(passadaEm(TERCA_AS_19H)).isEqualTo(1);
        outbox.deleteAll();

        // Uma hora depois, ainda dentro do mesmo expediente: a marca d'água já
        // existe, e não há segunda abertura a publicar.
        assertThat(passadaEm(TERCA_AS_20H))
                .as("a ADR-025 §5 dizia que o catálogo receberia dois eventos naquele "
                        + "dia. Com a marca d'água por (loja, expediente), ele recebe um — "
                        + "o produtor não chega a emitir o segundo.")
                .isZero();
        assertThat(outbox.findAll()).isEmpty();
    }

    @Test
    void a_madrugada_pertence_ao_expediente_de_ontem_e_nao_reabre() {
        pizzariaDas18As02();
        passadaEm(TERCA_AS_19H);
        outbox.deleteAll();

        // 01:00 de quarta ainda é o expediente de terça (hora de corte 04:00),
        // e a pizzaria segue aberta. Sem o dia operacional, este instante seria
        // "quarta" e publicaria uma abertura no meio da noite — reativando a
        // calabresa que acabou às 23h, que é exatamente o defeito que o
        // catalogo.md §3 descreve.
        assertThat(passadaEm(QUARTA_A_01H)).isZero();
        assertThat(outbox.findAll()).isEmpty();
    }

    @Test
    void o_expediente_do_payload_e_o_dia_operacional_e_nao_a_data_do_instante() {
        pizzariaDas18As02();

        assertThat(passadaEm(QUARTA_A_01H)).isEqualTo(1);

        assertThat(payloadDaUnicaLinha().get("expedienteDeReferencia").asString())
                .as("o instante é quarta; o expediente é terça")
                .isEqualTo("2026-09-22");
    }

    @Test
    void loja_sem_horario_nunca_abre_expediente() {
        lojas.salvar(LojaDeTeste.semHorario());

        assertThat(passadaEm(TERCA_AS_19H))
                .as("horário vazio significa 'nunca abre por horário' "
                        + "(estabelecimento.md §4) — quem opera só por aceite manual "
                        + "não reativa nada, e isso é o comportamento certo")
                .isZero();
    }

    @Test
    void a_linha_da_marca_dagua_e_o_evento_nascem_juntos() {
        UUID loja = pizzariaDas18As02();
        passadaEm(TERCA_AS_19H);

        assertThat(outbox.findAll()).hasSize(1);
        Boolean registrouDeNovo = transacao.execute(status ->
                aberturas.registrar(loja, LocalDate.of(2026, 9, 22), TERCA_AS_19H));
        assertThat(registrouDeNovo)
                .as("a marca d'água já está lá: uma segunda tentativa não insere")
                .isFalse();
    }

    // ── instantes, em hora civil de São Paulo (UTC−3) ───────────────────────

    private static final Instant TERCA_AS_15H = Instant.parse("2026-09-22T18:00:00Z");
    private static final Instant TERCA_AS_19H = Instant.parse("2026-09-22T22:00:00Z");
    private static final Instant TERCA_AS_20H = Instant.parse("2026-09-22T23:00:00Z");
    private static final Instant QUARTA_A_01H = Instant.parse("2026-09-23T04:00:00Z");
}
