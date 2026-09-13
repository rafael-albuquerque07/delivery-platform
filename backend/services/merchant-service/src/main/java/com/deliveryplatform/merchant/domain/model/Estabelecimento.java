package com.deliveryplatform.merchant.domain.model;

import com.deliveryplatform.valuetypes.Money;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A loja (`docs/dominio/estabelecimento.md` §1). Raiz de agregado.
 *
 * <p>Não guarda equipe nem vínculo de entregador: {@code Membro} e
 * {@code VinculoEntregador} são raízes próprias, pelo motivo que o documento
 * registra — o {@code Membro} é o objeto mais lido do sistema, e carregá-lo
 * junto traria a loja inteira a cada checagem de permissão.
 *
 * <p><b>O que ainda não está aqui e por quê.</b> {@code disponibilidade}
 * (horário e pausa) e {@code AreaDeEntrega} ficam para a rodada seguinte: a
 * faixa que cruza a meia-noite e a não-sobreposição de CEP (M10) são regra densa
 * o bastante para uma rodada própria, com o teste do pedido à 01:00 que o
 * documento chama de obrigatório. E {@code politicas.responsabilizaEntregadorPorNaoLiquidado}
 * fica de fora porque quem lê esse campo é o {@code settlement}, que não tem
 * código — escrever coluna para consumidor de marco distante é precisamente a
 * armadilha que o {@code CLAUDE.md} acabou de ganhar.
 *
 * <p><b>Não há mutador.</b> Nenhum caso de uso altera a loja ainda, e método
 * disponível é método que um dia é usado. Quando o primeiro caso de uso de
 * alteração existir, os campos deixam de ser {@code final} e o mutador nasce com
 * ele — junto com o {@code ConfiguracaoOperacionalAlteradaV1} que o
 * {@code contracts/eventos.md} já declara.
 */
public final class Estabelecimento {

    private final UUID id;
    private final Identificacao identificacao;
    private final Operacao operacao;
    private final PoliticaDeTroco politicaDeTroco;

    private Estabelecimento(
            UUID id, Identificacao identificacao, Operacao operacao, PoliticaDeTroco politicaDeTroco) {
        this.id = Objects.requireNonNull(id, "id");
        this.identificacao = Objects.requireNonNull(identificacao, "identificacao");
        this.operacao = Objects.requireNonNull(operacao, "operacao");
        this.politicaDeTroco = Objects.requireNonNull(politicaDeTroco, "politicaDeTroco");
    }

    /** Cadastro de loja nova. */
    public static Estabelecimento novo(
            Identificacao identificacao, Operacao operacao, PoliticaDeTroco politicaDeTroco) {
        return new Estabelecimento(UUID.randomUUID(), identificacao, operacao, politicaDeTroco);
    }

    /** Reconstrução do que já está persistido — usada pelo mapper de infraestrutura. */
    public static Estabelecimento reconstituir(
            UUID id, Identificacao identificacao, Operacao operacao, PoliticaDeTroco politicaDeTroco) {
        return new Estabelecimento(id, identificacao, operacao, politicaDeTroco);
    }

    public boolean aceita(Modalidade modalidade) {
        return operacao.aceita(modalidade);
    }

    public Set<MetodoPagamento> metodosDe(Modalidade modalidade) {
        return operacao.metodosDe(modalidade);
    }

    public Money pedidoMinimoDe(Modalidade modalidade) {
        return operacao.pedidoMinimoDe(modalidade);
    }

    public UUID getId() {
        return id;
    }

    public Identificacao getIdentificacao() {
        return identificacao;
    }

    public Operacao getOperacao() {
        return operacao;
    }

    public PoliticaDeTroco getPoliticaDeTroco() {
        return politicaDeTroco;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof Estabelecimento estabelecimento && id.equals(estabelecimento.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Sem documento, sem telefone e sem endereço. Os três são dado de
     * identificação, e {@code toString} é como um campo chega ao log sem ninguém
     * decidir isso — o {@code Usuario} do {@code identity-service} tem a mesma
     * nota pelo mesmo motivo.
     */
    @Override
    public String toString() {
        return "Estabelecimento{id=%s, nome=%s, modalidades=%s}"
                .formatted(id, identificacao.nome(), operacao.modalidadesAceitas());
    }
}
