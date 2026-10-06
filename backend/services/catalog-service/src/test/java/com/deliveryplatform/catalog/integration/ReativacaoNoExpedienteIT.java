package com.deliveryplatform.catalog.integration;

import com.deliveryplatform.catalog.application.port.in.ReativarNoExpediente;
import com.deliveryplatform.catalog.application.port.in.ReativarNoExpediente.Resultado;
import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.application.usecase.ReativacaoDeUmProduto;
import com.deliveryplatform.catalog.application.usecase.ReativarNoExpedienteService;
import com.deliveryplatform.catalog.domain.model.Disponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.Infraestrutura;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * A reativação contra o Mongo de verdade, e a versão do documento (ADR-052).
 *
 * <p>Nenhum destes casos é observável no teste unitário: a consulta, a versão e
 * o conflito precisam do banco, e o proxy precisa do Spring.
 *
 * <p>A reativação é chamada direto pela porta, sem HTTP: o chamador de verdade é
 * o consumidor do {@code ExpedienteAlteradoV1}, que nasce na G-C3b. A loja é a
 * {@link ProdutoDeTeste#LOJA}, e a coleção é limpa antes de cada caso — a forma
 * do {@code MarcacaoDeDisponibilidadeIT}.
 *
 * <h2>O que estes casos NÃO provam, e fica escrito</h2>
 *
 * <p><b>Nenhum caso percorre HTTP → 409.</b> Provar isso exigiria uma costura para
 * mudar o documento entre a leitura e a gravação do controlador. O que existe é
 * a exceção, provada aqui, e o tratador dela no {@code TratadorDeErros} —
 * <b>as duas metades, nunca a ponte</b>, e a segunda metade sem teste próprio.
 * <b>Gatilho escrito:</b> o primeiro relato de 409 que ninguém consegue explicar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReativacaoNoExpedienteIT extends Infraestrutura {

    private static final UUID LOJA = ProdutoDeTeste.LOJA;
    private static final LocalDate ABRIU = LocalDate.parse("2026-10-06");
    private static final LocalDate ONTEM = LocalDate.parse("2026-10-05");

    @Autowired ReativarNoExpediente reativacao;
    @Autowired ReativacaoDeUmProduto umProduto;
    @Autowired ProdutoRepositorio produtos;
    @Autowired MongoTemplate mongo;
    @Autowired PlatformTransactionManager transacoes;

    @BeforeEach
    void colecaoVazia() {
        mongo.getCollection("produtos").deleteMany(new Document());
    }

    // ── 1 · a consulta ──────────────────────────────────────────────────────

    @Test
    @DisplayName("1 · acha o produto e a opção carimbados antes, e não toca o resto")
    void a_consulta_acha_produto_e_opcao() {
        Produto a = produtos.salvar(ProdutoDeTeste.esgotadoHojeEm(ONTEM));
        Produto b = produtos.salvar(ProdutoDeTeste.esgotadoHojeEm(ABRIU));
        Produto c = produtos.salvar(ProdutoDeTeste.comOpcaoEsgotadaHojeEm(ONTEM));
        Produto d = ProdutoDeTeste.margheritaPublicada();
        d.marcar(Disponibilidade.acabando(Instant.parse("2026-10-05T20:00:00Z"), ONTEM));
        d = produtos.salvar(d);

        Resultado r = reativacao.reativar(LOJA, ABRIU);

        assertThat(r).isEqualTo(new Resultado(2, 0, 0));

        assertThat(relido(a).getDisponibilidade().estado())
                .isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);
        // B é a C11: marcado no expediente que abriu, continua esgotado.
        assertThat(relido(b).getDisponibilidade().estado())
                .isEqualTo(EstadoDeDisponibilidade.ESGOTADO_HOJE);
        assertThat(relido(d).getDisponibilidade().estado())
                .isEqualTo(EstadoDeDisponibilidade.ACABANDO);

        // No banco, relendo o documento cru: a opção de C voltou, e o carimbo
        // dela é nulo nos DOIS campos — meio par é o que a ADR-049 proíbe.
        Document opcao = primeiraOpcaoCrua(c.getId());
        Document disp = opcao.get("disponibilidade", Document.class);
        assertThat(disp.getString("estado")).isEqualTo("DISPONIVEL");
        assertThat(disp.get("marcadoEm")).isNull();
        assertThat(disp.get("expedienteDeReferencia")).isNull();
    }

    @Test
    @DisplayName("1b · o $elemMatch não casa estado de uma opção com carimbo de outra")
    void elem_match_nao_mistura_opcoes() {
        // Uma opção ESGOTADO_HOJE carimbada HOJE, e outra ACABANDO carimbada ONTEM.
        // Com caminho pontilhado, "estado = ESGOTADO_HOJE" e "carimbo < hoje"
        // casariam com opções diferentes — e o produto viraria candidato.
        Produto p = ProdutoDeTeste.margheritaPublicada();
        GrupoDeOpcoes tamanho = p.getGruposDeOpcoes().get(0);
        p.marcarOpcao(tamanho.id(), tamanho.opcoes().get(0).id(),
                Disponibilidade.esgotadoHoje(Instant.parse("2026-10-06T19:00:00Z"), ABRIU));
        p.marcarOpcao(tamanho.id(), tamanho.opcoes().get(1).id(),
                Disponibilidade.acabando(Instant.parse("2026-10-05T19:00:00Z"), ONTEM));
        produtos.salvar(p);

        assertThat(produtos.idsParaReativar(LOJA, ABRIU, 50)).isEmpty();
    }

    // ── 2 · a versão ────────────────────────────────────────────────────────

    @Test
    @DisplayName("2 · duas gravações a partir da mesma leitura: a segunda estoura, e a primeira sobrevive")
    void a_versao_bloqueia_a_atualizacao_perdida() {
        Produto gravado = produtos.salvar(ProdutoDeTeste.margheritaPublicada());
        GrupoDeOpcoes tamanho = gravado.getGruposDeOpcoes().get(0);
        UUID pequena = tamanho.opcoes().get(0).id();
        UUID media = tamanho.opcoes().get(1).id();

        Produto daMarli = produtos.buscarPorId(gravado.getId()).orElseThrow();
        Produto doJoao = produtos.buscarPorId(gravado.getId()).orElseThrow();

        daMarli.marcarOpcao(tamanho.id(), pequena,
                Disponibilidade.esgotadoHoje(Instant.parse("2026-10-06T19:00:00Z"), ABRIU));
        produtos.salvar(daMarli);

        doJoao.marcarOpcao(tamanho.id(), media,
                Disponibilidade.esgotadoHoje(Instant.parse("2026-10-06T19:00:01Z"), ABRIU));
        assertThatThrownBy(() -> produtos.salvar(doJoao))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // A prova de que o teste mede a atualização perdida, e não só a exceção:
        // a marcação da Marli continua no banco.
        assertThat(estadoDaOpcao(relido(gravado), pequena))
                .isEqualTo(EstadoDeDisponibilidade.ESGOTADO_HOJE);
    }

    /**
     * 2b · <b>medição</b>: o mesmo par de gravações, cada uma na sua transação,
     * entrelaçadas — que é como a marcação roda de verdade ({@code @Transactional}
     * com o {@code MongoTransactionManager}).
     *
     * <p>A transação do Mongo detecta conflito de escrita sozinha, e o que ela
     * levanta <b>não é</b> {@code OptimisticLockingFailureException}. Este caso
     * fixa o que chega, porque é isso que o {@code TratadorDeErros} e o laço da
     * reativação precisam saber tratar.
     */
    @Test
    @DisplayName("2b · entrelaçadas em transações, a segunda falha — e com que exceção")
    void em_transacao_o_conflito_chega_como() {
        Produto gravado = produtos.salvar(ProdutoDeTeste.margheritaPublicada());
        GrupoDeOpcoes tamanho = gravado.getGruposDeOpcoes().get(0);
        UUID pequena = tamanho.opcoes().get(0).id();
        UUID media = tamanho.opcoes().get(1).id();

        TransactionStatus primeira = transacoes.getTransaction(new DefaultTransactionDefinition());
        Produto daMarli = produtos.buscarPorId(gravado.getId()).orElseThrow();

        TransactionTemplate outra = new TransactionTemplate(transacoes);
        outra.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outra.executeWithoutResult(status -> {
            Produto doJoao = produtos.buscarPorId(gravado.getId()).orElseThrow();
            doJoao.marcarOpcao(tamanho.id(), media,
                    Disponibilidade.esgotadoHoje(Instant.parse("2026-10-06T19:00:01Z"), ABRIU));
            produtos.salvar(doJoao);
        });

        daMarli.marcarOpcao(tamanho.id(), pequena,
                Disponibilidade.esgotadoHoje(Instant.parse("2026-10-06T19:00:00Z"), ABRIU));
        Throwable falha = catchThrowable(() -> produtos.salvar(daMarli));
        transacoes.rollback(primeira);

        assertThat(falha).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(estadoDaOpcao(relido(gravado), media))
                .isEqualTo(EstadoDeDisponibilidade.ESGOTADO_HOJE);
    }

    // ── 3 · a inserção ──────────────────────────────────────────────────────

    @Test
    @DisplayName("3 · produto novo é inserção, e a gravação seguinte é atualização")
    void produto_novo_continua_sendo_insercao() {
        Produto novo = ProdutoDeTeste.margheritaPublicada();
        assertThat(novo.getVersao()).isNull();

        Produto inserido = produtos.salvar(novo);
        Produto relido = produtos.buscarPorId(inserido.getId()).orElseThrow();
        assertThat(relido.getVersao()).isNotNull();

        // A gravação de um produto EXISTENTE — sem ela, a §4.2 poderia estar
        // errada e todo o resto continuar verde.
        relido.marcar(Disponibilidade.esgotadoHoje(Instant.parse("2026-10-06T19:00:00Z"), ABRIU));
        Produto atualizado = produtos.salvar(relido);

        assertThat(atualizado.getVersao()).isGreaterThan(relido.getVersao());
        assertThat(mongo.getCollection("produtos").countDocuments()).isEqualTo(1);
    }

    // ── 4 · a varredura não é atômica ───────────────────────────────────────

    @Test
    @DisplayName("4 · falhando no segundo produto, o primeiro continua reativado")
    void a_varredura_nao_e_atomica() {
        Produto valido = produtos.salvar(ProdutoDeTeste.esgotadoHojeEm(ONTEM));

        // O segundo casa com o filtro e é ilegível — a releitura dele estoura. O
        // id é o maior possível para ele vir depois do válido na ordem por _id.
        UUID ultimo = UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");
        Document ilegivel = documentoCru(valido.getId());
        ilegivel.put("_id", ultimo);
        ilegivel.put("modoDeControle", "MODO_QUE_NAO_EXISTE");
        mongo.getCollection("produtos").insertOne(ilegivel);

        assertThatThrownBy(() -> reativacao.reativar(LOJA, ABRIU))
                .isInstanceOf(RuntimeException.class);

        assertThat(relido(valido).getDisponibilidade().estado())
                .isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);
    }

    // ── 5 e 6 · o proxy ─────────────────────────────────────────────────────

    @Test
    @DisplayName("5 · a unidade de trabalho é um proxy — o @Transactional dela vale")
    void a_unidade_de_trabalho_e_proxy() {
        assertThat(AopUtils.isAopProxy(umProduto)).isTrue();
    }

    @Test
    @DisplayName("6 · a varredura não tem @Transactional, nem na classe nem em método")
    void a_varredura_nao_tem_transacao() {
        assertThat(ReativarNoExpedienteService.class.isAnnotationPresent(Transactional.class))
                .isFalse();
        List<Method> anotados = Arrays.stream(ReativarNoExpedienteService.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Transactional.class))
                .toList();
        assertThat(anotados).isEmpty();
    }

    // ── apoio ───────────────────────────────────────────────────────────────

    private Produto relido(Produto p) {
        return produtos.buscarPorId(p.getId()).orElseThrow();
    }

    private static EstadoDeDisponibilidade estadoDaOpcao(Produto p, UUID opcaoId) {
        return p.getGruposDeOpcoes().stream()
                .flatMap(g -> g.opcoes().stream())
                .filter(o -> o.id().equals(opcaoId))
                .findFirst().orElseThrow()
                .disponibilidade().estado();
    }

    private Document documentoCru(UUID id) {
        Document doc = mongo.getCollection("produtos").find(new Document("_id", id)).first();
        assertThat(doc).as("documento %s", id).isNotNull();
        return doc;
    }

    @SuppressWarnings("unchecked")
    private Document primeiraOpcaoCrua(UUID produtoId) {
        List<Document> grupos = (List<Document>) documentoCru(produtoId).get("gruposDeOpcoes");
        List<Document> opcoes = (List<Document>) grupos.get(0).get("opcoes");
        return opcoes.get(0);
    }
}
