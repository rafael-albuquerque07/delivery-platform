package com.deliveryplatform.merchant.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

/**
 * As três colunas da pausa, agrupadas. Existe para o construtor da entidade não
 * crescer para dezessete parâmetros — com seis {@code String} adjacentes, trocar
 * dois de lugar compila e só quebra num teste que por acaso olhe os dois.
 *
 * <p><b>Nunca é nulo na leitura.</b> O Hibernate mapeia embutido com todos os
 * campos nulos para {@code null}, que é a armadilha clássica de
 * {@code @Embeddable} — aqui {@code pausa_ativa} é {@code NOT NULL}, então o
 * grupo sempre tem ao menos um valor e sempre volta como objeto.
 */
@Embeddable
public class PausaJpa {

    @Column(name = "pausa_ativa", nullable = false)
    private boolean ativa;

    @Column(name = "pausa_ate")
    private Instant ate;

    @Column(name = "pausa_motivo", length = 160)
    private String motivo;

    protected PausaJpa() {
        // exigido pelo JPA
    }

    public PausaJpa(boolean ativa, Instant ate, String motivo) {
        this.ativa = ativa;
        this.ate = ate;
        this.motivo = motivo;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public Instant getAte() {
        return ate;
    }

    public String getMotivo() {
        return motivo;
    }
}
