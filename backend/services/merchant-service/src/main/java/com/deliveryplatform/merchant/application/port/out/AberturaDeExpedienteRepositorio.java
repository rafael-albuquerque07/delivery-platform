package com.deliveryplatform.merchant.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A marca d'água da abertura do expediente (ADR-046 §1).
 *
 * <p>A porta tem um método, e ele devolve um booleano em vez de void porque
 * <b>é a resposta do banco que decide se há evento a publicar</b>. Não existe
 * {@code jaFoiPublicada(...)} para alguém chamar antes: um par
 * consultar-e-então-inserir teria janela entre as duas chamadas, e duas
 * instâncias da varredura publicariam o mesmo evento duas vezes.
 */
public interface AberturaDeExpedienteRepositorio {

    /**
     * Tenta registrar que a abertura deste expediente foi publicada.
     *
     * @return {@code true} se a linha foi criada agora — e portanto <b>este</b>
     *         chamador é quem deve gravar o evento; {@code false} se a abertura
     *         já estava registrada, por outra passada ou por outra instância.
     */
    boolean registrar(UUID estabelecimentoId, LocalDate expediente, Instant agora);
}
