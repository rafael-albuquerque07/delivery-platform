package com.deliveryplatform.merchant.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * Uma linha de {@code estabelecimento_area_faixa_cep}: um intervalo de CEP que
 * pertence a uma área.
 *
 * <p><b>Trio plano outra vez.</b> O domínio guarda as faixas <i>dentro</i> da
 * área, e JPA não aninha coleção de embutido dentro de coleção de embutido. A
 * tabela guarda a faixa com o identificador da área ao lado; quem devolve cada
 * faixa para a sua área é o mapper.
 *
 * <p>O CEP é {@code String} de oito dígitos pelo motivo que o
 * {@code FaixaDeCep} explica: guardado como número, o 01310-100 perde o zero da
 * frente.
 */
@Embeddable
public class FaixaDeCepJpa {

    @Column(name = "identificador_normalizado", nullable = false, length = 80)
    private String identificadorNormalizado;

    @Column(name = "cep_inicio", nullable = false, length = 8)
    private String cepInicio;

    @Column(name = "cep_fim", nullable = false, length = 8)
    private String cepFim;

    protected FaixaDeCepJpa() {
        // exigido pelo JPA
    }

    public FaixaDeCepJpa(String identificadorNormalizado, String cepInicio, String cepFim) {
        this.identificadorNormalizado = identificadorNormalizado;
        this.cepInicio = cepInicio;
        this.cepFim = cepFim;
    }

    public String getIdentificadorNormalizado() {
        return identificadorNormalizado;
    }

    public String getCepInicio() {
        return cepInicio;
    }

    public String getCepFim() {
        return cepFim;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof FaixaDeCepJpa faixa
                && Objects.equals(identificadorNormalizado, faixa.identificadorNormalizado)
                && Objects.equals(cepInicio, faixa.cepInicio)
                && Objects.equals(cepFim, faixa.cepFim);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identificadorNormalizado, cepInicio, cepFim);
    }
}
