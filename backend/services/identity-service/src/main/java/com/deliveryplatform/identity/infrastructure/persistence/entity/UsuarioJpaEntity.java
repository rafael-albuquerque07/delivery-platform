package com.deliveryplatform.identity.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade JPA — modelo de persistência, não modelo de domínio (README.md: a
 * duplicação é intencional). Estrutura vem de {@code V1__cria_usuario.sql};
 * {@code ddl-auto: validate} recusa subir se os dois divergirem.
 */
@Entity
@Table(name = "usuario")
public class UsuarioJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, unique = true, length = 16)
    private String telefone;

    @Column(name = "telefone_verificado_em", nullable = false)
    private Instant telefoneVerificadoEm;

    @Column(length = 254)
    private String email;

    @Column(name = "email_verificado_em")
    private Instant emailVerificadoEm;

    @Column(name = "hash_da_senha", nullable = false, length = 120)
    private String hashDaSenha;

    protected UsuarioJpaEntity() {
        // exigido pelo JPA — acesso por campo, não por este construtor
    }

    public UsuarioJpaEntity(
            UUID id,
            String nome,
            String telefone,
            Instant telefoneVerificadoEm,
            String email,
            Instant emailVerificadoEm,
            String hashDaSenha) {
        this.id = id;
        this.nome = nome;
        this.telefone = telefone;
        this.telefoneVerificadoEm = telefoneVerificadoEm;
        this.email = email;
        this.emailVerificadoEm = emailVerificadoEm;
        this.hashDaSenha = hashDaSenha;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public Instant getTelefoneVerificadoEm() {
        return telefoneVerificadoEm;
    }

    public String getEmail() {
        return email;
    }

    public Instant getEmailVerificadoEm() {
        return emailVerificadoEm;
    }

    public String getHashDaSenha() {
        return hashDaSenha;
    }
}
