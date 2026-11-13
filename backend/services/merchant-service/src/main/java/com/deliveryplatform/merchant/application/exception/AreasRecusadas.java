package com.deliveryplatform.merchant.application.exception;

import java.util.Objects;

/**
 * O domínio recusou as áreas de entrega que o corpo descrevia — 400 (ADR-081).
 *
 * <p>No molde do {@link HorarioRecusado} e da {@link OperacaoRecusada}: o domínio recusa com
 * {@code IllegalArgumentException}, e a tradução nasce em <b>um</b> ponto só — o
 * {@code DefinirAreasDeEntregaService}, em volta das três construções que podem recusar. O
 * motivo diz <b>qual</b> construção recusou; a frase é do tratador, e a mensagem do domínio fica
 * na causa.
 */
public class AreasRecusadas extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Motivo {
        /** {@code FaixaDeCep}: CEP que não tem oito dígitos, ou faixa com o início depois do fim. */
        CEP_INVALIDO,
        /** {@code AreaDeEntrega}: taxa negativa (E3), ou a mesma faixa duas vezes na mesma área. */
        AREA_INVALIDA,
        /**
         * {@code Estabelecimento}: duas áreas com o mesmo identificador normalizado (M9), ou
         * faixas de CEP que se cruzam entre áreas ativas (M10). O construtor as recusa juntas, e
         * a frase cobre as duas.
         */
        AREAS_EM_CONFLITO
    }

    private final Motivo motivo;

    public AreasRecusadas(Motivo motivo, Throwable causa) {
        super(Objects.requireNonNull(motivo, "motivo") + ": " + causa.getMessage(), causa);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
