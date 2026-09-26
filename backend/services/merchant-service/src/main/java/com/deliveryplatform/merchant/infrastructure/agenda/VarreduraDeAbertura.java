package com.deliveryplatform.merchant.infrastructure.agenda;

import com.deliveryplatform.merchant.application.usecase.PublicarAberturaDeExpediente;
import com.deliveryplatform.merchant.config.ExpedienteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * O relógio que chama o observador.
 *
 * <p><b>Isto não é o job à meia-noite que o {@code catalogo.md} §3 rejeita.</b>
 * A objeção de lá é ao <i>horário fixo</i>: um job à meia-noite zeraria a
 * calabresa que acabou às 23h enquanto a pizzaria ainda vende. Esta varredura
 * não tem hora — ela pergunta a cada minuto quem entrou no horário, e cada loja
 * abre no horário dela, no fuso dela.
 *
 * <p><b>Por que varredura e não agendador por loja.</b> Um agendador daria
 * reativação no segundo certo, e custaria reagendar a cada mudança de horário,
 * de fuso e de pausa, reconstruir a agenda a cada reinício, e falhar em
 * silêncio numa loja só — que é o pior modo de falha possível, porque ninguém
 * olha a loja que não reclamou. A varredura é uma consulta: não tem estado a
 * reconstruir e falha para todo mundo de uma vez, que é visível.
 *
 * <p>A classe é fina de propósito: ela só tem o relógio. A regra mora no caso
 * de uso, que é onde um teste a alcança sem esperar um minuto.
 */
@Component
@EnableConfigurationProperties(ExpedienteProperties.class)
@ConditionalOnProperty(
        name = "delivery.expediente.varredura-habilitada",
        havingValue = "true", matchIfMissing = true)
public class VarreduraDeAbertura {

    private static final Logger log = LoggerFactory.getLogger(VarreduraDeAbertura.class);

    private final PublicarAberturaDeExpediente publicar;

    public VarreduraDeAbertura(PublicarAberturaDeExpediente publicar) {
        this.publicar = publicar;
    }

    @Scheduled(
            fixedDelayString = "${delivery.expediente.intervalo-ms:60000}",
            initialDelayString = "${delivery.expediente.atraso-inicial-ms:10000}")
    public void passada() {
        try {
            int publicadas = publicar.umaPassada();
            if (publicadas > 0) {
                log.info("expediente: {} abertura(s) publicada(s)", publicadas);
            }
        } catch (RuntimeException e) {
            // Uma passada que estoura não pode matar o agendador: com
            // fixedDelay, exceção que escapa encerra a tarefa e ninguém
            // percebe até alguém reclamar que o cardápio não reativa. A
            // próxima passada tenta de novo, e a marca d'água garante que
            // nada é republicado.
            log.error("expediente: a varredura falhou nesta passada", e);
        }
    }
}
