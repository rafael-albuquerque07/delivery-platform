package com.deliveryplatform.catalog.infrastructure.messaging;

import com.deliveryplatform.catalog.application.port.in.ReativarNoExpediente;
import com.deliveryplatform.catalog.infrastructure.messaging.LeitorDeExpedienteAlterado.AberturaDeExpediente;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * O primeiro consumidor deste repositório que <b>escreve em banco</b>.
 *
 * <p>Tudo o que ele decide está em dois lugares, e nenhum dos dois é aqui: o
 * {@link LeitorDeExpedienteAlterado} decide o que o corpo significa, e a
 * {@link ConfiguracaoDoConsumoDeExpediente} decide o que acontece quando dá errado.
 * Este método é a costura.
 *
 * <p><b>Três coisas que ele não faz, e cada uma por um motivo:</b>
 *
 * <ul>
 *   <li><b>não confirma a mensagem à mão.</b> O {@code OuvinteDeVinculoAlterado}
 *       confirma sempre, num {@code finally} — ali, perder um evento custa um cache
 *       frio. Aqui, a confirmação é do contêiner, e a exceção <b>tem</b> de escapar:
 *       é ela que aciona a retentativa e a fila morta;</li>
 *   <li><b>não tem {@code try/catch}.</b> Pela mesma razão. Engolir aqui desligaria a
 *       ADR-026 em silêncio, e a loja que abriu ficaria com o catálogo indisponível sem
 *       erro em lugar nenhum;</li>
 *   <li><b>não é {@code @Transactional}.</b> Esta é a mais fácil de errar. A
 *       {@code ReativarNoExpedienteService} varre em lotes e <b>depende</b> de cada
 *       produto ter a sua própria transação — foi por isso que a G-C3a pôs a escrita num
 *       bean separado. Uma transação aqui envolveria o lote inteiro e ressuscitaria a
 *       corrida que o {@code @Version} fechou.</li>
 * </ul>
 *
 * <p><b>Por que ele não guarda que já viu esta mensagem.</b> A invariante 7 do
 * {@code CLAUDE.md} manda {@code processed_messages} para quem escreve em banco. Este
 * consumidor é a segunda exceção escrita a ela (ADR-057): o efeito dele é <b>comparar</b>
 * o expediente que vem no evento com o carimbo que está gravado, e comparar duas vezes
 * dá o mesmo resultado que comparar uma. Reentrega não é problema aqui — é trabalho
 * repetido e nada mais.
 */
@Component
public class OuvinteDeExpedienteAlterado {

    private static final Logger log = LoggerFactory.getLogger(OuvinteDeExpedienteAlterado.class);

    private final ReativarNoExpediente reativar;

    public OuvinteDeExpedienteAlterado(ReativarNoExpediente reativar) {
        this.reativar = reativar;
    }

    @RabbitListener(
            queues = "#{filaDeExpedienteAlterado.name}",
            containerFactory = ConfiguracaoDoConsumoDeExpediente.FABRICA)
    public void receber(String corpo) {
        Optional<AberturaDeExpediente> abertura = LeitorDeExpedienteAlterado.ler(corpo);

        if (abertura.isEmpty()) {
            log.debug("evento de expediente que não é abertura — confirmado sem efeito");
            return;
        }

        AberturaDeExpediente aberta = abertura.get();

        ReativarNoExpediente.Resultado resultado =
                reativar.reativar(aberta.estabelecimentoId(), aberta.expediente());

        log.info(
                "reativação no expediente {} da loja {}: {}",
                aberta.expediente(),
                aberta.estabelecimentoId(),
                resultado);
    }
}
