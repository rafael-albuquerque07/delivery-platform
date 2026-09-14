package com.deliveryplatform.merchant.domain.model;

import com.deliveryplatform.valuetypes.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 * <p><b>{@code AreaDeEntrega} fica dentro, e é aqui que M9 e M10 vivem.</b> São
 * invariantes <b>entre</b> áreas — identificador único na loja, faixas de CEP
 * que não se cruzam — e só quem enxerga todas de uma vez consegue verificá-las.
 * Fora do agregado isso viraria consulta concorrente, e a sobreposição entraria
 * pelo vão.
 *
 * <p><b>O que ainda não está aqui.</b>
 * {@code politicas.responsabilizaEntregadorPorNaoLiquidado} continua de fora
 * porque quem lê esse campo é o {@code settlement}, que não tem código.
 *
 * <p><b>Não há mutador.</b> Nenhum caso de uso altera a loja ainda, e método
 * disponível é método que um dia é usado. Quando o primeiro existir, os campos
 * deixam de ser {@code final} e o mutador nasce com o evento que o
 * {@code contracts/eventos.md} já declara.
 */
public final class Estabelecimento {

    private final UUID id;
    private final Identificacao identificacao;
    private final Operacao operacao;
    private final PoliticaDeTroco politicaDeTroco;
    private final Disponibilidade disponibilidade;
    private final List<AreaDeEntrega> areasDeEntrega;

    private Estabelecimento(
            UUID id,
            Identificacao identificacao,
            Operacao operacao,
            PoliticaDeTroco politicaDeTroco,
            Disponibilidade disponibilidade,
            List<AreaDeEntrega> areasDeEntrega) {
        this.id = Objects.requireNonNull(id, "id");
        this.identificacao = Objects.requireNonNull(identificacao, "identificacao");
        this.operacao = Objects.requireNonNull(operacao, "operacao");
        this.politicaDeTroco = Objects.requireNonNull(politicaDeTroco, "politicaDeTroco");
        this.disponibilidade = Objects.requireNonNull(disponibilidade, "disponibilidade");
        this.areasDeEntrega = List.copyOf(exigirAreasCoerentes(areasDeEntrega));
    }

    public static Estabelecimento novo(
            Identificacao identificacao,
            Operacao operacao,
            PoliticaDeTroco politicaDeTroco,
            Disponibilidade disponibilidade,
            List<AreaDeEntrega> areasDeEntrega) {
        return new Estabelecimento(
                UUID.randomUUID(),
                identificacao,
                operacao,
                politicaDeTroco,
                disponibilidade,
                areasDeEntrega);
    }

    /** Reconstrução do que já está persistido — usada pelo mapper de infraestrutura. */
    public static Estabelecimento reconstituir(
            UUID id,
            Identificacao identificacao,
            Operacao operacao,
            PoliticaDeTroco politicaDeTroco,
            Disponibilidade disponibilidade,
            List<AreaDeEntrega> areasDeEntrega) {
        return new Estabelecimento(
                id, identificacao, operacao, politicaDeTroco, disponibilidade, areasDeEntrega);
    }

    /**
     * M9 e M10, nos escopos que cada uma pede — e eles são diferentes de
     * propósito.
     *
     * <p><b>M9 vale entre todas as áreas, ativas ou não.</b> O que ela impede é
     * a Marli cadastrar "Boa Viagem" duas vezes sem perceber; desativar uma
     * delas não desfaz a confusão, só a esconde.
     *
     * <p><b>M10 vale só entre as ativas.</b> O que ela impede é o mesmo endereço
     * resolver para duas taxas — e área desativada não cota nada. Se uma área
     * desativada voltasse a ser ativada com faixa sobreposta, a construção
     * recusaria, porque reativar é construir de novo.
     */
    private static List<AreaDeEntrega> exigirAreasCoerentes(List<AreaDeEntrega> areas) {
        Objects.requireNonNull(areas, "areasDeEntrega");

        Set<String> identificadores = new HashSet<>();
        for (AreaDeEntrega area : areas) {
            Objects.requireNonNull(area, "área de entrega");
            if (!identificadores.add(area.identificadorNormalizado())) {
                throw new IllegalArgumentException(
                        "área de entrega repetida nesta loja: %s (M9)"
                                .formatted(area.identificadorNormalizado()));
            }
        }

        List<AreaDeEntrega> ativas = areas.stream().filter(AreaDeEntrega::ativa).toList();
        for (int i = 0; i < ativas.size(); i++) {
            for (int j = i + 1; j < ativas.size(); j++) {
                cruzamentoDeCep(ativas.get(i), ativas.get(j));
            }
        }
        return new ArrayList<>(areas);
    }

    private static void cruzamentoDeCep(AreaDeEntrega uma, AreaDeEntrega outra) {
        for (FaixaDeCep daUma : uma.faixasDeCep()) {
            for (FaixaDeCep daOutra : outra.faixasDeCep()) {
                if (daUma.sobrepoe(daOutra)) {
                    throw new IllegalArgumentException(
                            ("faixas de CEP se sobrepõem entre %s (%s) e %s (%s) — o mesmo "
                                    + "endereço resolveria para duas taxas (M10)")
                                    .formatted(uma.nome(), daUma, outra.nome(), daOutra));
                }
            }
        }
    }

    /**
     * O caminho normal da ADR-020: o cliente diz o bairro. Compara pelo
     * identificador normalizado, então "boa viagem" acha "Boa Viagem".
     *
     * <p>Vazio quando não há área <b>ativa</b> com esse nome. Nunca uma taxa
     * zero por falta de resposta — M11.
     */
    public Optional<AreaDeEntrega> areaPorNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return Optional.empty();
        }
        String procurado = AreaDeEntrega.normalizar(nome);
        return areasDeEntrega.stream()
                .filter(AreaDeEntrega::ativa)
                .filter(area -> area.identificadorNormalizado().equals(procurado))
                .findFirst();
    }

    /**
     * O refinamento: resolver o endereço pelo CEP, sem perguntar o bairro.
     *
     * <p>Vazio quando nenhuma área ativa cobre o CEP — M11 de novo. E a resposta
     * é única porque M10 garante que as faixas das ativas não se cruzam.
     */
    public Optional<AreaDeEntrega> areaPara(String cep) {
        String normalizado = FaixaDeCep.cep(cep);
        return areasDeEntrega.stream()
                .filter(AreaDeEntrega::ativa)
                .filter(area -> area.cobre(normalizado))
                .findFirst();
    }

    /**
     * A resposta que a {@code OperacaoDoEstabelecimentoPort} devolve, composta
     * num lugar só: horário, pausa e o fuso da própria loja.
     */
    public boolean estaAberta(Instant agora) {
        return disponibilidade.abertaEm(agora, identificacao.fusoHorario());
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

    public Disponibilidade getDisponibilidade() {
        return disponibilidade;
    }

    public List<AreaDeEntrega> getAreasDeEntrega() {
        return areasDeEntrega;
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
     * decidir isso.
     */
    @Override
    public String toString() {
        return "Estabelecimento{id=%s, nome=%s, modalidades=%s, areas=%d}"
                .formatted(id, identificacao.nome(), operacao.modalidadesAceitas(),
                        areasDeEntrega.size());
    }
}
