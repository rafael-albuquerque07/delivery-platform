package com.deliveryplatform.catalog.infrastructure.persistence.migration;

import io.mongock.api.annotations.BeforeExecution;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackBeforeExecution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * Os dois índices mínimos da §7 do {@code catalogo.md} — o primeiro
 * {@code changeUnit} que este repositório executa.
 *
 * <pre>
 * (estabelecimentoId, estadoDePublicacao)
 * (estabelecimentoId, categoriaId, ordem)
 * </pre>
 *
 * <p><b>Os dois começam por {@code estabelecimentoId} e não é acaso.</b>
 * Nenhuma consulta deste serviço atravessa lojas: o cardápio é sempre de uma
 * loja. Um índice que não comece pelo discriminador obriga o Mongo a varrer
 * documentos de todo mundo para responder sobre um.
 *
 * <p><b>Por que o {@code vendavel} não tem índice.</b> Porque ele não é campo
 * (§4, e {@code Produto.vendavel()}). "Só os vendáveis" é filtro de aplicação
 * sobre o resultado do primeiro índice. O dia em que o cardápio de uma loja não
 * couber em memória, a saída é projeção com dono e invalidação escritos — não
 * um booleano solto que cinco caminhos de escrita têm de manter em dia.
 *
 * <p><b>Os índices têm nome.</b> A §7 não os nomeia; nomear é decisão desta
 * rodada e é barata: índice com nome gerado é índice que ninguém consegue
 * derrubar num {@code changeUnit} futuro sem copiar a chave inteira.
 *
 * <p><b>A reversão existe e provavelmente nunca será exercida</b> — a própria
 * ADR-007 avisa disso. Ela está aqui porque escrever a volta na hora da ida é
 * quando ainda é barato pensar nela, e porque derrubar dois índices é a única
 * reversão que realmente é segura.
 *
 * <p><b>Os índices nascem em {@code @BeforeExecution}, e não em
 * {@code @Execution}.</b> Com o {@code MongoTransactionManager} no contexto
 * (a {@code MongoConfig}), o Mongock roda o {@code @Execution} dentro de uma
 * transação — e o MongoDB recusa {@code createIndexes} numa transação com
 * {@code readConcern: majority} (erro 72, {@code InvalidOptions}). O
 * {@code @BeforeExecution} é o lugar que o Mongock reserva para DDL: roda
 * <b>fora</b> da transação, antes dela. Descoberto na G-B1, no primeiro teste
 * que subiu com os dois beans juntos.
 *
 * <p>O {@code @Execution} fica vazio, e não é esquecimento: a anotação é
 * obrigatória, e esta unidade não transforma dado nenhum. Desligar a
 * transação do Mongock inteiro ({@code mongock.transaction-strategy}) teria
 * resolvido com uma linha e custado a atomicidade da primeira transformação
 * de dado que vier — que é exatamente a unidade que precisa dela.
 */
@ChangeUnit(id = "001-cria-indices-do-produto", order = "001", author = "delivery-platform")
public class V001CriaIndicesDoProduto {

    static final String COLECAO = "produtos";
    static final String POR_ESTADO = "produto_por_estabelecimento_e_estado";
    static final String POR_CATEGORIA = "produto_por_estabelecimento_categoria_e_ordem";

    @BeforeExecution
    public void criarIndices(MongoTemplate mongo) {
        mongo.indexOps(COLECAO).createIndex(
                new Index()
                        .on("estabelecimentoId", Sort.Direction.ASC)
                        .on("estadoDePublicacao", Sort.Direction.ASC)
                        .named(POR_ESTADO));

        mongo.indexOps(COLECAO).createIndex(
                new Index()
                        .on("estabelecimentoId", Sort.Direction.ASC)
                        .on("categoriaId", Sort.Direction.ASC)
                        .on("ordem", Sort.Direction.ASC)
                        .named(POR_CATEGORIA));
    }

    @RollbackBeforeExecution
    public void derrubarIndices(MongoTemplate mongo) {
        mongo.indexOps(COLECAO).dropIndex(POR_ESTADO);
        mongo.indexOps(COLECAO).dropIndex(POR_CATEGORIA);
    }

    /** Nenhum dado a transformar — ver o javadoc da classe. */
    @Execution
    public void executar() {
    }

    @RollbackExecution
    public void reverter() {
    }
}
