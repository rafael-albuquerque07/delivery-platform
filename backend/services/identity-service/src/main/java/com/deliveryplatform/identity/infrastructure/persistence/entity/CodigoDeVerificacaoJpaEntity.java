package com.deliveryplatform.identity.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade JPA do código de verificação. Estrutura vem de
 * {@code V2__cria_codigo_de_verificacao.sql}; {@code ddl-auto: validate} recusa
 * subir se os dois divergirem.
 *
 * <p><b>Sem chave estrangeira para {@code usuario}</b>, e não é esquecimento: o
 * código existe justamente enquanto o usuário <i>não</i> existe.
 */
@Entity
@Table(name = "codigo_de_verificacao")
public class CodigoDeVerificacaoJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 16)
    private String telefone;

    @Column(nullable = false, length = 6)
    private String codigo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(nullable = false)
    private int tentativas;

    protected CodigoDeVerificacaoJpaEntity() {
        // exigido pelo JPA — acesso por campo, não por este construtor
    }

    public CodigoDeVerificacaoJpaEntity(
            UUID id, String telefone, String codigo, Instant criadoEm, Instant expiraEm, int tentativas) {
        this.id = id;
        this.telefone = telefone;
        this.codigo = codigo;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
        this.tentativas = tentativas;
    }

    public UUID getId() {
        return id;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getCodigo() {
        return codigo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public int getTentativas() {
        return tentativas;
    }
}
