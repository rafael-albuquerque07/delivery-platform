package com.deliveryplatform.merchant.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Uma linha de {@code estabelecimento_area_entrega}.
 *
 * <p>O {@code identificadorNormalizado} é <b>derivado do nome</b> no domínio e
 * gravado aqui porque é a chave da área dentro da loja: é ele que a chave
 * primária usa para fazer M9 valer também no banco, e é por ele que a tabela de
 * faixas de CEP se liga à área.
 */
@Embeddable
public class AreaDeEntregaJpa {

    @Column(name = "identificador_normalizado", nullable = false, length = 80)
    private String identificadorNormalizado;

    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    @Column(name = "taxa", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxa;

    @Column(name = "ativa", nullable = false)
    private boolean ativa;

    protected AreaDeEntregaJpa() {
        // exigido pelo JPA
    }

    public AreaDeEntregaJpa(
            String identificadorNormalizado, String nome, BigDecimal taxa, boolean ativa) {
        this.identificadorNormalizado = identificadorNormalizado;
        this.nome = nome;
        this.taxa = taxa;
        this.ativa = ativa;
    }

    public String getIdentificadorNormalizado() {
        return identificadorNormalizado;
    }

    public String getNome() {
        return nome;
    }

    public BigDecimal getTaxa() {
        return taxa;
    }

    public boolean isAtiva() {
        return ativa;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof AreaDeEntregaJpa area
                && Objects.equals(identificadorNormalizado, area.identificadorNormalizado);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(identificadorNormalizado);
    }
}
