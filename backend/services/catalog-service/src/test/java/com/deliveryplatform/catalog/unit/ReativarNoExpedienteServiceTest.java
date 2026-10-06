package com.deliveryplatform.catalog.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deliveryplatform.catalog.application.port.in.ReativarNoExpediente.Resultado;
import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.application.usecase.ReativacaoDeUmProduto;
import com.deliveryplatform.catalog.application.usecase.ReativarNoExpedienteService;
import com.deliveryplatform.catalog.config.ReativacaoProperties;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * A varredura da reativação, sem Mongo e sem Spring.
 *
 * <p><b>O que este arquivo não consegue provar</b>, e por isso está escrito aqui:
 * que o {@code @Transactional} do {@link ReativacaoDeUmProduto} vale. Sem o proxy
 * do Spring não há transação nenhuma, e a chamada direta passa igual. Quem prova é
 * o {@code ReativacaoNoExpedienteIT}, e é por isso que ele existe.
 */
class ReativarNoExpedienteServiceTest {

    private static final UUID LOJA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate ABRIU = LocalDate.of(2026, 10, 6);
    private static final LocalDate ONTEM = LocalDate.of(2026, 10, 5);

    // ─────────────────────────────────────────────────────────── o caso central

    @Test
    @DisplayName("produto esgotado num expediente anterior volta ao cardápio")
    void reativa_o_que_acabou_antes() {
        Produto p = ProdutoDeTeste.esgotadoHojeEm(ONTEM);
        RepositorioFalso repo = new RepositorioFalso().com(p);

        Resultado r = servico(repo).reativar(LOJA, ABRIU);

        assertThat(r.produtosAlterados()).isEqualTo(1);
        assertThat(r.candidatosSemMudanca()).isZero();
        assertThat(repo.gravados).containsExactly(p.getId());
    }

    @Test
    @DisplayName("o predicado vence a consulta: carimbo do próprio expediente não reativa")
    void o_predicado_vence_a_consulta() {
        // A consulta devolve o id — é o que a §4 manda o teste simular, porque é a
        // única forma de provar que a decisão não está nela.
        Produto p = ProdutoDeTeste.esgotadoHojeEm(ABRIU);
        RepositorioFalso repo = new RepositorioFalso().com(p);

        Resultado r = servico(repo).reativar(LOJA, ABRIU);

        assertThat(r.produtosAlterados()).isZero();
        assertThat(r.candidatosSemMudanca()).isEqualTo(1);
        assertThat(repo.gravados).isEmpty();
    }

    @Test
    @DisplayName("evento velho reentregue: o que acabou depois do expediente dele não volta")
    void evento_velho_nao_reativa_o_que_acabou_depois() {
        // O caso que separa isBefore de !equals — e é o único. O carimbo do
        // próprio expediente não separa: os dois dizem "não". Um evento de ONTEM
        // chegando depois de alguém marcar hoje é a C11, e foi o defeito da G-C1.
        Produto p = ProdutoDeTeste.esgotadoHojeEm(ABRIU);
        RepositorioFalso repo = new RepositorioFalso().com(p);

        Resultado r = servico(repo).reativar(LOJA, ONTEM);

        assertThat(r.produtosAlterados()).isZero();
        assertThat(repo.gravados).isEmpty();
    }

    @Test
    @DisplayName("opção esgotada antes reativa, e o produto é gravado inteiro")
    void reativa_a_opcao() {
        Produto p = ProdutoDeTeste.comOpcaoEsgotadaHojeEm(ONTEM);
        RepositorioFalso repo = new RepositorioFalso().com(p);

        Resultado r = servico(repo).reativar(LOJA, ABRIU);

        assertThat(r.produtosAlterados()).isEqualTo(1);
        assertThat(repo.gravados).containsExactly(p.getId());
    }

    // ───────────────────────────────────────────────────── a guarda do laço

    @Test
    @DisplayName("lote inteiro sem mudança para a varredura em vez de girar para sempre")
    void lote_sem_progresso_para() {
        // Dois candidatos que o predicado recusa. A consulta devolveria os mesmos
        // na volta seguinte, porque nada mudou. Sem a guarda, isto não termina.
        RepositorioFalso repo = new RepositorioFalso()
                .com(ProdutoDeTeste.esgotadoHojeEm(ABRIU))
                .com(ProdutoDeTeste.esgotadoHojeEm(ABRIU));

        Resultado r = servico(repo).reativar(LOJA, ABRIU);

        assertThat(r.candidatosSemMudanca()).isEqualTo(2);
        assertThat(r.produtosAlterados()).isZero();
        assertThat(repo.consultas).isEqualTo(1);
    }

    @Test
    @DisplayName("a varredura só para quando a consulta devolve lote vazio")
    void varre_em_lotes_ate_esvaziar() {
        RepositorioFalso repo = new RepositorioFalso()
                .com(ProdutoDeTeste.esgotadoHojeEm(ONTEM))
                .com(ProdutoDeTeste.esgotadoHojeEm(ONTEM))
                .com(ProdutoDeTeste.esgotadoHojeEm(ONTEM));

        Resultado r = servico(repo, new ReativacaoProperties(2, 3)).reativar(LOJA, ABRIU);

        assertThat(r.produtosAlterados()).isEqualTo(3);
        // dois lotes de dois e um de zero: a consulta roda três vezes
        assertThat(repo.consultas).isEqualTo(3);
    }

    // ─────────────────────────────────────────────────────────── o conflito

    @Test
    @DisplayName("conflito de versão é refeito, e o conflito aparece no resultado")
    void conflito_e_refeito() {
        Produto p = ProdutoDeTeste.esgotadoHojeEm(ONTEM);
        RepositorioFalso repo = new RepositorioFalso().com(p).conflitosNaGravacao(1);

        Resultado r = servico(repo).reativar(LOJA, ABRIU);

        assertThat(r.produtosAlterados()).isEqualTo(1);
        assertThat(r.conflitosResolvidos()).isEqualTo(1);
    }

    @Test
    @DisplayName("conflito que não cede estoura, e a varredura não relata sucesso")
    void conflito_persistente_sobe() {
        Produto p = ProdutoDeTeste.esgotadoHojeEm(ONTEM);
        RepositorioFalso repo = new RepositorioFalso().com(p).conflitosNaGravacao(99);

        assertThatThrownBy(() -> servico(repo).reativar(LOJA, ABRIU))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("o teto é o teto: tantos conflitos quanto tentativas estoura, sem uma a mais")
    void o_teto_de_tentativas_e_exato() {
        // Com 99 conflitos, um teto frouxo ainda estoura — só uma volta depois. É
        // com exatamente "tentativas" conflitos que um teto frouxo passaria.
        Produto p = ProdutoDeTeste.esgotadoHojeEm(ONTEM);
        RepositorioFalso repo = new RepositorioFalso().com(p).conflitosNaGravacao(3);

        assertThatThrownBy(() -> servico(repo, new ReativacaoProperties(50, 3)).reativar(LOJA, ABRIU))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repo.gravados).isEmpty();
    }

    @Test
    @DisplayName("produto que desapareceu entre a consulta e a releitura não é erro")
    void produto_que_sumiu() {
        RepositorioFalso repo = new RepositorioFalso().comIdFantasma();

        Resultado r = servico(repo).reativar(LOJA, ABRIU);

        assertThat(r.produtosAlterados()).isZero();
        assertThat(r.candidatosSemMudanca()).isZero();
    }

    @Test
    @DisplayName("loja sem nada esgotado não grava nada e não falha")
    void loja_limpa() {
        Resultado r = servico(new RepositorioFalso()).reativar(LOJA, ABRIU);

        assertThat(r).isEqualTo(Resultado.NADA);
    }

    // ───────────────────────────────────────────────────────────── montagem

    private ReativarNoExpedienteService servico(RepositorioFalso repo) {
        return servico(repo, new ReativacaoProperties(50, 3));
    }

    private ReativarNoExpedienteService servico(
            RepositorioFalso repo, ReativacaoProperties props) {
        return new ReativarNoExpedienteService(
                repo, new ReativacaoDeUmProduto(repo), props);
    }

    /**
     * Dublê escrito à mão porque a porta tem mais de um método — a forma de lambda
     * que a G-C2 usou não serve aqui.
     *
     * <p>Ele imita a propriedade que faz o laço convergir: <b>produto gravado sai
     * da lista de candidatos</b>, que é o que o filtro do Mongo faz de verdade.
     *
     * <p>E imita a outra que a repetição exige: <b>cada leitura devolve uma cópia</b>
     * do que foi gravado por último, como o banco devolve. Devolvendo o mesmo
     * objeto, a primeira tentativa mutaria o produto guardado, a gravação
     * estouraria, e a segunda tentativa leria o produto <i>já reativado</i> — o
     * conflito pareceria "nada a fazer". Foi o que a versão do pacote fazia.
     */
    private static final class RepositorioFalso implements ProdutoRepositorio {
        private final Map<UUID, Produto> porId = new HashMap<>();
        private final Deque<UUID> candidatos = new ArrayDeque<>();
        final List<UUID> gravados = new ArrayList<>();
        int consultas = 0;
        private int conflitosRestantes = 0;

        RepositorioFalso com(Produto p) {
            porId.put(p.getId(), p);
            candidatos.add(p.getId());
            return this;
        }

        /** Um id que a consulta devolve e que não existe mais ao ser relido. */
        RepositorioFalso comIdFantasma() {
            candidatos.add(UUID.randomUUID());
            return this;
        }

        RepositorioFalso conflitosNaGravacao(int quantos) {
            this.conflitosRestantes = quantos;
            return this;
        }

        @Override
        public List<UUID> idsParaReativar(UUID estabelecimentoId, LocalDate expediente, int limite) {
            consultas++;
            List<UUID> lote = new ArrayList<>();
            for (UUID id : candidatos) {
                if (lote.size() == limite) {
                    break;
                }
                lote.add(id);
            }
            return lote;
        }

        @Override
        public Optional<Produto> buscarPorId(UUID id) {
            return Optional.ofNullable(porId.get(id)).map(RepositorioFalso::copia);
        }

        @Override
        public Produto salvar(Produto produto) {
            if (conflitosRestantes > 0) {
                conflitosRestantes--;
                throw new OptimisticLockingFailureException(
                        "versao do produto " + produto.getId() + " mudou");
            }
            porId.put(produto.getId(), copia(produto));
            gravados.add(produto.getId());
            candidatos.remove(produto.getId());
            return produto;
        }

        /** A varredura não lista publicados; o dublê não finge que lista. */
        @Override
        public Page<Produto> publicadosDe(UUID estabelecimentoId, Pageable paginacao) {
            throw new UnsupportedOperationException("fora do que este teste exercita");
        }

        private static Produto copia(Produto p) {
            return Produto.reconstituir(p.getId(), p.getEstabelecimentoId(), p.getCategoriaId(),
                    p.getNome(), p.getDescricao(), p.getImagemRef(), p.getPrecoBase(),
                    p.getOrdem(), p.getEstadoDePublicacao(), p.getModoDeControle(),
                    p.getDisponibilidade(), p.getGruposDeOpcoes(), p.getVersao());
        }
    }
}
