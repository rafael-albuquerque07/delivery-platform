package com.deliveryplatform.merchant.infrastructure.persistence.entity;

import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.Papel;
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
 * Entidade JPA do vínculo. Estrutura vem de {@code V4__cria_membro.sql};
 * {@code ddl-auto: validate} recusa subir se os dois divergirem.
 *
 * <p><b>Uma coleção só, e {@code EAGER} sem remorso.</b> O
 * {@code EstabelecimentoJpaEntity} tem cinco, e a medida da A2b mostrou o que
 * isso custa: um {@code select} com cinco {@code left join}, trafegando o
 * produto das cinco coleções. Com uma, não há produto — o {@code join} devolve
 * uma linha por permissão e nada se multiplica. E este é o objeto mais lido do
 * sistema: buscar o vínculo sem as permissões seria buscar de novo em seguida.
 */
@Entity
@Table(name = "membro")
public class MembroJpaEntity {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Papel papel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoDoMembro estado;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "alterado_em", nullable = false)
    private Instant alteradoEm;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "membro_permissao", joinColumns = @JoinColumn(name = "membro_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permissao", nullable = false, length = 24)
    private Set<Permissao> permissoes = EnumSet.noneOf(Permissao.class);

    protected MembroJpaEntity() {
        // exigido pelo JPA — acesso por campo, não por este construtor
    }

    public MembroJpaEntity(
            UUID id,
            UUID usuarioId,
            UUID estabelecimentoId,
            Papel papel,
            EstadoDoMembro estado,
            Set<Permissao> permissoes,
            Instant criadoEm,
            Instant alteradoEm) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.estabelecimentoId = estabelecimentoId;
        this.papel = papel;
        this.estado = estado;
        this.permissoes = permissoes.isEmpty()
                ? EnumSet.noneOf(Permissao.class)
                : EnumSet.copyOf(permissoes);
        this.criadoEm = criadoEm;
        this.alteradoEm = alteradoEm;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getEstabelecimentoId() {
        return estabelecimentoId;
    }

    public Papel getPapel() {
        return papel;
    }

    public EstadoDoMembro getEstado() {
        return estado;
    }

    public Set<Permissao> getPermissoes() {
        return permissoes;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAlteradoEm() {
        return alteradoEm;
    }
}
