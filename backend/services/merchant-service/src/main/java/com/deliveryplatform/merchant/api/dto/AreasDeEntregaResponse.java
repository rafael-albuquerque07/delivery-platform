package com.deliveryplatform.merchant.api.dto;

import com.deliveryplatform.merchant.domain.model.AreaDeEntrega;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * As áreas como estão gravadas, ordenadas pelo identificador normalizado — a ordem do banco não
 * é de ninguém. O CEP sai com os oito dígitos, sem formatação.
 */
public record AreasDeEntregaResponse(List<AreaResponse> areasDeEntrega) {

    public record AreaResponse(String nome, BigDecimal taxa, boolean ativa, List<FaixaDeCepResponse> faixasDeCep) {
    }

    public record FaixaDeCepResponse(String inicio, String fim) {
    }

    public static AreasDeEntregaResponse de(List<AreaDeEntrega> areas) {
        return new AreasDeEntregaResponse(areas.stream()
                .sorted(Comparator.comparing(AreaDeEntrega::identificadorNormalizado))
                .map(area -> new AreaResponse(area.nome(), area.taxa().valor(), area.ativa(),
                        area.faixasDeCep().stream()
                                .map(f -> new FaixaDeCepResponse(f.inicio(), f.fim()))
                                .toList()))
                .toList());
    }
}
