package com.deliveryplatform.merchant.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Um intervalo de CEP, inclusivo nas duas pontas (`estabelecimento.md` §5).
 * Opcional numa área: a ADR-020 modela entrega por <b>bairro nomeado</b>, e a
 * faixa de CEP é o refinamento de quem quer resolver o endereço sem perguntar.
 *
 * <p><b>O CEP é texto, e isso não é preguiça.</b> Guardado como número, o CEP
 * 01310-100 — a Avenida Paulista — vira 1310100, e o zero da frente só volta se
 * alguém lembrar de formatar com oito casas. Metade dos CEPs de São Paulo começa
 * com zero. Como texto de oito dígitos com largura fixa, a comparação
 * lexicográfica <b>é</b> a comparação numérica, e o valor que sai é o valor que
 * entrou.
 */
public record FaixaDeCep(String inicio, String fim) implements Comparable<FaixaDeCep> {

    private static final Pattern NAO_DIGITO = Pattern.compile("\\D");
    private static final int DIGITOS = 8;

    public FaixaDeCep {
        inicio = exigirCep(inicio, "inicio");
        fim = exigirCep(fim, "fim");
        if (inicio.compareTo(fim) > 0) {
            throw new IllegalArgumentException(
                    "faixa de CEP invertida: %s vem depois de %s".formatted(inicio, fim));
        }
    }

    /** Aceita a formatação que o comerciante digita: {@code 50000-000}. */
    public static FaixaDeCep de(String inicio, String fim) {
        return new FaixaDeCep(inicio, fim);
    }

    /** Faixa de um CEP só. */
    public static FaixaDeCep unica(String cep) {
        return new FaixaDeCep(cep, cep);
    }

    public boolean contem(String cep) {
        String normalizado = cep(cep);
        return inicio.compareTo(normalizado) <= 0 && normalizado.compareTo(fim) <= 0;
    }

    /**
     * Normaliza e valida um CEP solto — oito dígitos, sem formatação. Pública
     * porque quem procura área por CEP precisa validar a entrada <b>antes</b> de
     * varrer as áreas: sem isso, uma loja sem faixa nenhuma devolveria "não
     * achei" para um CEP inválido, em vez de reclamar da entrada.
     */
    public static String cep(String bruto) {
        return exigirCep(bruto, "o valor");
    }

    /**
     * Duas faixas se cruzam quando cada uma começa antes de a outra terminar.
     * É a forma que não tem caso especial: não importa qual vem primeiro nem se
     * uma contém a outra.
     */
    public boolean sobrepoe(FaixaDeCep outra) {
        Objects.requireNonNull(outra, "outra");
        return inicio.compareTo(outra.fim) <= 0 && outra.inicio.compareTo(fim) <= 0;
    }

    private static String exigirCep(String bruto, String campo) {
        if (bruto == null) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        String digitos = NAO_DIGITO.matcher(bruto).replaceAll("");
        if (digitos.length() != DIGITOS) {
            throw new IllegalArgumentException(
                    "%s não é CEP: esperados %d dígitos, vieram %d"
                            .formatted(campo, DIGITOS, digitos.length()));
        }
        return digitos;
    }

    @Override
    public int compareTo(FaixaDeCep outra) {
        int porInicio = inicio.compareTo(outra.inicio);
        return porInicio != 0 ? porInicio : fim.compareTo(outra.fim);
    }

    @Override
    public String toString() {
        return "%s–%s".formatted(inicio, fim);
    }
}
