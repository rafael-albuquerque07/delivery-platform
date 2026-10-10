package com.deliveryplatform.merchant.application.exception;

/**
 * O agregado recusou a loja que o corpo descrevia — 400 (ADR-060).
 *
 * <p>Nasce de <b>um</b> ponto só: a construção do agregado no
 * {@code CriarEstabelecimentoService}. É por isso que existe uma exceção própria, e
 * não um tratador de {@code IllegalArgumentException}: um tratador global
 * transformaria em 400 todo defeito do serviço que lançasse o mesmo tipo, e
 * esconderia bug como erro do cliente.
 *
 * <p>A mensagem é a do agregado, como ele a escreveu — a tela a mostra como veio
 * (ADR-055).
 */
public class CadastroDeLojaRecusado extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CadastroDeLojaRecusado(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
