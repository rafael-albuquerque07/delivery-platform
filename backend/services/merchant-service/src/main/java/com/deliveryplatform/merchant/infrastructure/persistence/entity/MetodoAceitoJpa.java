package com.deliveryplatform.merchant.infrastructure.persistence.entity;

import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

/**
 * Uma linha de {@code estabelecimento_metodo_aceito}: o par
 * (modalidade, método) que a loja aceita.
 *
 * <p><b>Par e não mapa de conjuntos.</b> O domínio guarda
 * {@code Map<Modalidade, Set<MetodoPagamento>>}, e JPA não persiste mapa de
 * coleção diretamente. A tabela guarda o par plano; quem reagrupa é o mapper,
 * escrito à mão — que é justamente o trabalho pelo qual ele existe.
 *
 * <p><b>Classe e não {@code record}.</b> O Hibernate 7 aceita {@code record} como
 * {@code @Embeddable}, mas eu não confirmei isso no jar resolvido, e este
 * repositório já apanhou cinco vezes de API que a memória lembra e a versão não
 * tem. Classe com {@code equals}/{@code hashCode} funciona em toda versão, e é o
 * que um {@code Set} de embutidos exige.
 */
@Embeddable
public class MetodoAceitoJpa {

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade", nullable = false, length = 16)
    private Modalidade modalidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo", nullable = false, length = 16)
    private MetodoPagamento metodo;

    protected MetodoAceitoJpa() {
        // exigido pelo JPA
    }

    public MetodoAceitoJpa(Modalidade modalidade, MetodoPagamento metodo) {
        this.modalidade = modalidade;
        this.metodo = metodo;
    }

    public Modalidade getModalidade() {
        return modalidade;
    }

    public MetodoPagamento getMetodo() {
        return metodo;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof MetodoAceitoJpa par
                && modalidade == par.modalidade
                && metodo == par.metodo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(modalidade, metodo);
    }
}
