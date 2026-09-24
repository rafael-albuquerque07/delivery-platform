package com.deliveryplatform.merchant.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A linha do outbox.
 *
 * <p>Não tem par no domínio — é peça de infraestrutura inteira, e por isso vive
 * aqui e não tem entidade de domínio correspondente. O que o domínio conhece é
 * {@link com.deliveryplatform.merchant.domain.evento.EventoDeDominio}, que não
 * sabe que existe tabela.
 */
@Entity
@Table(name = "outbox")
public class OutboxJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String tipo;

    @Column(nullable = false)
    private short versao;

    @Column(nullable = false, length = 60)
    private String agregado;

    @Column(name = "agregado_id", nullable = false)
    private UUID agregadoId;

    @Column(name = "chave_de_rota", nullable = false, length = 200)
    private String chaveDeRota;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;

    @Column(name = "publicado_em")
    private Instant publicadoEm;

    @Column(nullable = false)
    private short tentativas;

    @Column(name = "ultimo_erro")
    private String ultimoErro;

    protected OutboxJpaEntity() {
        // JPA
    }

    OutboxJpaEntity(UUID id, String tipo, short versao, String agregado, UUID agregadoId,
                    String chaveDeRota, String payload, Instant ocorridoEm) {
        this.id = id;
        this.tipo = tipo;
        this.versao = versao;
        this.agregado = agregado;
        this.agregadoId = agregadoId;
        this.chaveDeRota = chaveDeRota;
        this.payload = payload;
        this.ocorridoEm = ocorridoEm;
        this.tentativas = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public short getVersao() {
        return versao;
    }

    public String getAgregado() {
        return agregado;
    }

    public UUID getAgregadoId() {
        return agregadoId;
    }

    public String getChaveDeRota() {
        return chaveDeRota;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    public Instant getPublicadoEm() {
        return publicadoEm;
    }

    public short getTentativas() {
        return tentativas;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }

    void publicado(Instant quando) {
        this.publicadoEm = quando;
        this.ultimoErro = null;
    }

    /**
     * Registra a falha de uma tentativa.
     *
     * <p>Guarda a <b>classe e a mensagem</b> da exceção, truncadas. Não guarda a
     * pilha: pilha em coluna de banco é log com outro nome, e a regra da
     * CLAUDE.md sobre o que não pode ir para o log vale para esta coluna.
     */
    void falhou(String motivo) {
        this.tentativas = (short) Math.min(this.tentativas + 1, Short.MAX_VALUE);
        this.ultimoErro = motivo == null ? null : motivo.substring(0, Math.min(motivo.length(), 500));
    }
}
