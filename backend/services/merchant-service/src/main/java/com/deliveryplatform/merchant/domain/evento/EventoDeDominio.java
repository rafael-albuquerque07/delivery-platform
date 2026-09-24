package com.deliveryplatform.merchant.domain.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Um fato que já aconteceu no domínio e que sai do serviço.
 *
 * <p>O nome está no passado de propósito. Um evento não pede nada e não pode ser
 * recusado por quem o recebe: ele diz o que foi, e cada consumidor decide o que
 * fazer com isso. Se algum dia aparecer aqui um tipo cujo nome esteja no
 * imperativo, é comando disfarçado, e comando não passa por fila de eventos.
 *
 * <p><b>O que esta interface não tem.</b> Não tem {@code eventId}. O
 * identificador do evento é a chave primária da linha de outbox, e quem a gera é
 * o adaptador, no instante da gravação — o domínio não sabe que existe outbox.
 * É também por isso que o {@code eventId} do envelope é estável: ele nasce uma
 * vez, junto com a linha, e o relay pode reenviar quantas vezes precisar sem
 * inventar um novo.
 *
 * @see com.deliveryplatform.merchant.application.port.out.Outbox
 */
public interface EventoDeDominio {

    /**
     * O {@code eventType} do envelope: {@code VinculoAlterado}, <b>sem</b> a
     * versão — ela vive em {@link #versao()} ({@code contracts/README.md}).
     */
    String tipo();

    /** O {@code eventVersion} do envelope. */
    short versao();

    /** Agregado de onde o fato saiu: {@code membro}, {@code estabelecimento}. */
    String agregado();

    /** Identificador da instância do agregado — não do estabelecimento, quando forem diferentes. */
    UUID agregadoId();

    /**
     * Chave de rota do topic exchange: {@code merchant.vinculo.alterado.v1}.
     *
     * <p>Quem publica escolhe a chave; quem consome escolhe o padrão. É o que
     * permite um consumidor assinar {@code merchant.vinculo.#} sem que o
     * {@code merchant} saiba que ele existe.
     */
    String chaveDeRota();

    /**
     * Quando o fato aconteceu — <b>não</b> quando foi publicado.
     *
     * <p>É cláusula de contrato, não carimbo informativo: a ordem de publicação
     * não é garantida (ADR-043 §4), e o consumidor descarta evento mais velho do
     * que o último que aplicou comparando este instante.
     */
    Instant ocorridoEm();
}
