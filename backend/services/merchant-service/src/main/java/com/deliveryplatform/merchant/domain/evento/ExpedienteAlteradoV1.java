package com.deliveryplatform.merchant.domain.evento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * O expediente de um estabelecimento mudou.
 *
 * <p>Hoje o único motivo é a abertura, e o único consumidor que reage a ela com
 * regra de domínio é o {@code catalog-service}, que reativa todo produto e opção
 * {@code ESGOTADO_HOJE} cujo {@code expedienteDeReferencia} seja diferente do
 * que vem aqui ({@code catalogo.md} §3).
 *
 * <p><b>{@code occurredAt} e {@code expedienteDeReferencia} são coisas
 * diferentes, e é de propósito.</b> O primeiro é o instante da publicação; o
 * segundo é o dia operacional (ADR-025). Às 01:30 de domingo o instante é
 * domingo e o expediente é sábado. Um sistema que usasse o carimbo do envelope
 * como dia erraria uma vez por dia, na madrugada, que é justamente quando a
 * pizzaria está vendendo.
 *
 * <p><b>Por que o expediente viaja no payload.</b> Sem ele, o consumidor teria
 * de perguntar ao {@code merchant} qual é o expediente corrente a cada evento —
 * e a idempotência de C11 passaria a depender de duas chamadas darem a mesma
 * resposta, num intervalo que atravessa a hora de corte todo dia. Com ele, o
 * consumidor compara dois valores que recebeu.
 *
 * @see <a href="../../../../../../../../../docs/architecture/decisions/ADR-046-quem-observa-a-abertura-do-expediente.md">ADR-046</a>
 */
public record ExpedienteAlteradoV1(
        UUID estabelecimentoId,
        MotivoDoExpediente motivo,
        LocalDate expedienteDeReferencia,
        Instant ocorridoEm
) implements EventoDeDominio {

    public static ExpedienteAlteradoV1 abertura(UUID estabelecimentoId,
                                                LocalDate expediente,
                                                Instant ocorridoEm) {
        return new ExpedienteAlteradoV1(
                estabelecimentoId, MotivoDoExpediente.ABERTURA_DE_EXPEDIENTE,
                expediente, ocorridoEm);
    }

    @Override
    public String tipo() {
        return "ExpedienteAlterado";
    }

    @Override
    public short versao() {
        return 1;
    }

    @Override
    public String agregado() {
        return "estabelecimento";
    }

    @Override
    public UUID agregadoId() {
        return estabelecimentoId;
    }

    /**
     * {@code merchant.expediente.alterado.v1}.
     *
     * <p>O nome tem história: este evento se chamava
     * {@code DisponibilidadeAlteradaV1} e colidia com um evento de mesmo nome
     * no {@code catalog}, que significa outra coisa. A ADR-031 renomeou
     * <b>este</b>, porque o {@code catalog} nomeava um conceito de domínio e o
     * {@code merchant} nomeava um atributo solto para um fato que o resto do
     * repositório já chamava de expediente.
     */
    @Override
    public String chaveDeRota() {
        return "merchant.expediente.alterado.v1";
    }
}
