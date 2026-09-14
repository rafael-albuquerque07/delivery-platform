package com.deliveryplatform.merchant.infrastructure.persistence.entity;

import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.TipoDeOperacao;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Entidade JPA — modelo de persistência, não de domínio (a duplicação é
 * intencional). Estrutura vem de {@code V1__cria_estabelecimento.sql};
 * {@code ddl-auto: validate} recusa subir se os dois divergirem.
 *
 * <p><b>Dinheiro em uma coluna só.</b> A ADR-009 especifica {@code Money} como
 * <i>"value object imutável com {@code BigDecimal} de escala 2 e código de
 * moeda, persistido como {@code @Column(precision = 19, scale = 2)}"</i> — a
 * moeda está no tipo e não na coluna, e isso é a ADR falando, não eu decidindo.
 * Faz sentido: a moeda existe para que somar reais com outra coisa estoure, não
 * para suportar multimoeda, e gravar as mesmas três letras em toda linha de toda
 * tabela de cinco serviços defenderia contra um caso que a ADR diz que não vem.
 * A volta passa por {@code Money.de(BigDecimal)}, que assume BRL — se um dia
 * houver segunda moeda, é migration por coluna.
 *
 * <p><b>As três coleções são {@code EAGER}, de propósito.</b> O agregado é
 * carregado inteiro por definição, e as três são limitadas: duas modalidades,
 * três métodos, e no máximo alguns turnos por dia da semana. {@code LAZY} faria
 * o mapper depender de uma sessão aberta — e o mapper roda onde o repositório
 * for chamado, com {@code open-in-view: false}.
 */
@Entity
@Table(name = "estabelecimento")
public class EstabelecimentoJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 14)
    private String documento;

    @Column(nullable = false, length = 16)
    private String telefone;

    @Column(name = "endereco_textual", nullable = false, length = 240)
    private String enderecoTextual;

    @Column(nullable = false, length = 80)
    private String bairro;

    @Column(name = "fuso_horario", nullable = false, length = 40)
    private String fusoHorario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_de_operacao", nullable = false, length = 16)
    private TipoDeOperacao tipoDeOperacao;

    @Column(name = "desconto_de_retirada", nullable = false, precision = 19, scale = 2)
    private BigDecimal descontoDeRetirada;

    @Column(name = "fundo_maximo_de_troco", nullable = false, precision = 19, scale = 2)
    private BigDecimal fundoMaximoDeTroco;

    @Column(name = "aceita_pedido_sem_troco_disponivel", nullable = false)
    private boolean aceitaPedidoSemTrocoDisponivel;

    @Embedded
    private PausaJpa pausa;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "estabelecimento_metodo_aceito",
            joinColumns = @JoinColumn(name = "estabelecimento_id"))
    private Set<MetodoAceitoJpa> metodosAceitos = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "estabelecimento_pedido_minimo",
            joinColumns = @JoinColumn(name = "estabelecimento_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "modalidade", length = 16)
    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private Map<Modalidade, BigDecimal> pedidoMinimo = new LinkedHashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "estabelecimento_horario",
            joinColumns = @JoinColumn(name = "estabelecimento_id"))
    private Set<HorarioJpa> horarios = new LinkedHashSet<>();

    protected EstabelecimentoJpaEntity() {
        // exigido pelo JPA — acesso por campo, não por este construtor
    }

    public EstabelecimentoJpaEntity(
            UUID id,
            String nome,
            String documento,
            String telefone,
            String enderecoTextual,
            String bairro,
            String fusoHorario,
            TipoDeOperacao tipoDeOperacao,
            BigDecimal descontoDeRetirada,
            BigDecimal fundoMaximoDeTroco,
            boolean aceitaPedidoSemTrocoDisponivel,
            PausaJpa pausa,
            Set<MetodoAceitoJpa> metodosAceitos,
            Map<Modalidade, BigDecimal> pedidoMinimo,
            Set<HorarioJpa> horarios) {
        this.id = id;
        this.nome = nome;
        this.documento = documento;
        this.telefone = telefone;
        this.enderecoTextual = enderecoTextual;
        this.bairro = bairro;
        this.fusoHorario = fusoHorario;
        this.tipoDeOperacao = tipoDeOperacao;
        this.descontoDeRetirada = descontoDeRetirada;
        this.fundoMaximoDeTroco = fundoMaximoDeTroco;
        this.aceitaPedidoSemTrocoDisponivel = aceitaPedidoSemTrocoDisponivel;
        this.pausa = pausa;
        this.metodosAceitos = new LinkedHashSet<>(metodosAceitos);
        this.pedidoMinimo = new LinkedHashMap<>(pedidoMinimo);
        this.horarios = new LinkedHashSet<>(horarios);
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDocumento() {
        return documento;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getEnderecoTextual() {
        return enderecoTextual;
    }

    public String getBairro() {
        return bairro;
    }

    public String getFusoHorario() {
        return fusoHorario;
    }

    public TipoDeOperacao getTipoDeOperacao() {
        return tipoDeOperacao;
    }

    public BigDecimal getDescontoDeRetirada() {
        return descontoDeRetirada;
    }

    public BigDecimal getFundoMaximoDeTroco() {
        return fundoMaximoDeTroco;
    }

    public boolean isAceitaPedidoSemTrocoDisponivel() {
        return aceitaPedidoSemTrocoDisponivel;
    }

    public PausaJpa getPausa() {
        return pausa;
    }

    public Set<HorarioJpa> getHorarios() {
        return horarios;
    }

    public Set<MetodoAceitoJpa> getMetodosAceitos() {
        return metodosAceitos;
    }

    public Map<Modalidade, BigDecimal> getPedidoMinimo() {
        return pedidoMinimo;
    }
}
