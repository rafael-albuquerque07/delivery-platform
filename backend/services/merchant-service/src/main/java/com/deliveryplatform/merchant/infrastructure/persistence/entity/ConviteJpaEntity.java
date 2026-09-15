package com.deliveryplatform.merchant.infrastructure.persistence.entity;

import com.deliveryplatform.merchant.domain.model.EstadoDoConvite;
import com.deliveryplatform.merchant.domain.model.Permissao;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entidade JPA do convite. Estrutura vem de {@code V5__cria_convite.sql};
 * {@code ddl-auto: validate} recusa subir se os dois divergirem.
 *
 * <p>Uma coleção só, {@code EAGER} — mesma leitura do {@code MembroJpaEntity}:
 * sem segunda coleção não há produto cartesiano, e um convite sem as permissões
 * oferecidas é meio convite.
 */
@Entity
@Table(name = "convite")
public class ConviteJpaEntity {

    @Id
    private UUID id;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Column(nullable = false, length = 16)
    private String telefone;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "convidado_por", nullable = false)
    private UUID convidadoPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoDoConvite estado;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "aceito_em")
    private Instant aceitoEm;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "convite_permissao", joinColumns = @JoinColumn(name = "convite_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permissao", nullable = false, length = 24)
    private Set<Permissao> permissoes = EnumSet.noneOf(Permissao.class);

    protected ConviteJpaEntity() {
        // exigido pelo JPA — acesso por campo, não por este construtor
    }

    public ConviteJpaEntity(
            UUID id,
            UUID estabelecimentoId,
            String telefone,
            String token,
            UUID convidadoPor,
            EstadoDoConvite estado,
            Set<Permissao> permissoes,
            Instant criadoEm,
            Instant expiraEm,
            Instant aceitoEm) {
        this.id = id;
        this.estabelecimentoId = estabelecimentoId;
        this.telefone = telefone;
        this.token = token;
        this.convidadoPor = convidadoPor;
        this.estado = estado;
        this.permissoes = permissoes.isEmpty()
                ? EnumSet.noneOf(Permissao.class)
                : EnumSet.copyOf(permissoes);
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
        this.aceitoEm = aceitoEm;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEstabelecimentoId() {
        return estabelecimentoId;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getToken() {
        return token;
    }

    public UUID getConvidadoPor() {
        return convidadoPor;
    }

    public EstadoDoConvite getEstado() {
        return estado;
    }

    public Set<Permissao> getPermissoes() {
        return permissoes;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getAceitoEm() {
        return aceitoEm;
    }
}
