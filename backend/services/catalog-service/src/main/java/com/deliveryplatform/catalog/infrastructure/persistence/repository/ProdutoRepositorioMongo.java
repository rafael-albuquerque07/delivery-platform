package com.deliveryplatform.catalog.infrastructure.persistence.repository;

import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.infrastructure.persistence.entity.ProdutoDocumento;
import com.deliveryplatform.catalog.infrastructure.persistence.mapper.ProdutoMapper;
import com.mongodb.MongoException;
import org.bson.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * O adaptador: recebe e devolve agregado, grava e lê documento.
 *
 * <p>Um caminho de mapeamento só, nos dois sentidos, pelo
 * {@link ProdutoMapper}. A regra é a mesma que a rodada F cobrou do
 * {@code merchant}: quem escrever um segundo caminho de mapeamento cria duas
 * verdades sobre o mesmo documento, e a segunda envelhece.
 */
@Repository
public class ProdutoRepositorioMongo implements ProdutoRepositorio {

    private static final String ESGOTADO_HOJE = EstadoDeDisponibilidade.ESGOTADO_HOJE.name();

    private final ProdutoSpringDataRepository documentos;
    private final MongoTemplate mongo;

    public ProdutoRepositorioMongo(ProdutoSpringDataRepository documentos, MongoTemplate mongo) {
        this.documentos = documentos;
        this.mongo = mongo;
    }

    /**
     * Grava o documento inteiro, e traduz os <b>dois</b> jeitos de o conflito
     * chegar numa única exceção (ADR-052).
     *
     * <p>Fora de transação, o {@code @Version} recusa a gravação velha com
     * {@code OptimisticLockingFailureException}. <b>Dentro</b> de uma — que é
     * como a marcação e a reativação rodam —, quem percebe primeiro é o próprio
     * Mongo: a transação que tenta gravar um documento alterado depois do início
     * dela falha com o erro 112, {@code WriteConflict}, e o Spring Data o entrega
     * como {@code DataIntegrityViolationException}. Medido no
     * {@code ReativacaoNoExpedienteIT}, caso 2b.
     *
     * <p>Os dois dizem a mesma coisa — alguém gravou no meio —, e quem chama
     * precisa de uma resposta só: 409 na marcação, repetir na reativação. Sem
     * esta tradução, o conflito real sairia como 500 e a repetição nunca
     * aconteceria.
     */
    @Override
    public Produto salvar(Produto produto) {
        try {
            return ProdutoMapper.paraDominio(
                    documentos.save(ProdutoMapper.paraDocumento(produto)));
        } catch (DataIntegrityViolationException falha) {
            if (conflitoDeEscrita(falha)) {
                throw new OptimisticLockingFailureException(
                        "o produto " + produto.getId() + " foi gravado por outra transação", falha);
            }
            throw falha;
        }
    }

    private static final int WRITE_CONFLICT = 112;

    private static boolean conflitoDeEscrita(Throwable falha) {
        for (Throwable causa = falha; causa != null; causa = causa.getCause()) {
            if (causa instanceof MongoException mongo && mongo.getCode() == WRITE_CONFLICT) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Produto> buscarPorId(UUID id) {
        return documentos.findById(id).map(ProdutoMapper::paraDominio);
    }

    @Override
    public Page<Produto> publicadosDe(UUID estabelecimentoId, Pageable paginacao) {
        // Page.map preserva o total. Converter para lista e reembrulhar num
        // PageImpl perderia o getTotalElements(), que é justamente o que o
        // cliente não consegue recalcular.
        return documentos
                .findByEstabelecimentoIdAndEstadoDePublicacao(
                        estabelecimentoId, EstadoDePublicacao.ATIVO.name(), paginacao)
                .map(ProdutoMapper::paraDominio);
    }

    /**
     * A primeira consulta deste repositório sobre array aninhado.
     *
     * <pre>
     * { estabelecimentoId: loja,
     *   $or: [ { disponibilidade.estado: ESGOTADO_HOJE,
     *            disponibilidade.expedienteDeReferencia: { $lt: expediente } },
     *          { gruposDeOpcoes: { $elemMatch: { opcoes: { $elemMatch: {
     *              disponibilidade.estado: ESGOTADO_HOJE,
     *              disponibilidade.expedienteDeReferencia: { $lt: expediente } } } } } } ] }
     * </pre>
     *
     * <p><b>O {@code $elemMatch} aninhado é obrigatório.</b> Com caminho pontilhado
     * ({@code gruposDeOpcoes.opcoes.disponibilidade.estado}) cada condição casaria
     * com <i>qualquer</i> opção: um produto com uma opção esgotada hoje e
     * <i>outra</i> com carimbo antigo casaria sem que nenhuma opção tivesse as duas
     * coisas. O {@code ReativacaoNoExpedienteIT} tem o caso.
     *
     * <p><b>{@code $lt} sobre texto</b>, autorizado pelo {@code catalogo.md} §3:
     * <i>"o texto ISO gravado no Mongo ordena lexicograficamente — {@code $lt}
     * serve"</i>.
     *
     * <p><b>Não há índice para {@code disponibilidade.estado}.</b> O filtro usa o
     * prefixo {@code estabelecimentoId}, que o {@code V001} indexa, então a
     * varredura é dentro da loja. <b>Gatilho escrito:</b> a primeira loja com mais
     * de mil produtos, ou a primeira varredura que apareça em log de consulta
     * lenta — aí nasce o {@code 002}.
     *
     * <p>Só o {@code _id} volta: a unidade de trabalho relê cada produto na
     * transação dela, e é a versão de lá que vale (ADR-052).
     */
    @Override
    public List<UUID> idsParaReativar(UUID estabelecimentoId, LocalDate expediente, int limite) {
        String dia = expediente.toString();

        Criteria doProduto = Criteria.where("disponibilidade.estado").is(ESGOTADO_HOJE)
                .and("disponibilidade.expedienteDeReferencia").lt(dia);

        Criteria daOpcao = Criteria.where("gruposDeOpcoes").elemMatch(
                Criteria.where("opcoes").elemMatch(
                        Criteria.where("disponibilidade.estado").is(ESGOTADO_HOJE)
                                .and("disponibilidade.expedienteDeReferencia").lt(dia)));

        Query consulta = new Query(Criteria.where("estabelecimentoId").is(estabelecimentoId)
                .orOperator(doProduto, daOpcao))
                // Ordem estável: sem ela, a ordem do lote é a natural do disco,
                // que o Mongo não garante — e a varredura deixaria de ser repetível.
                .with(Sort.by("_id"))
                .limit(limite);
        consulta.fields().include("_id");

        // Lido como Document cru, e não como ProdutoDocumento: com a projeção só
        // de _id, o record seria montado com todo o resto nulo — um documento que
        // não existe, a um passo de alguém gravá-lo.
        return mongo.find(consulta, Document.class, mongo.getCollectionName(ProdutoDocumento.class))
                .stream()
                .map(documento -> documento.get("_id", UUID.class))
                .toList();
    }
}
