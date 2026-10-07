package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.application.exception.AcessoNegado;
import com.deliveryplatform.catalog.application.exception.ProdutoNaoEncontrado;
import com.deliveryplatform.catalog.application.port.out.AutorizacaoComercialPort;
import com.deliveryplatform.catalog.application.port.out.ContextoDeAcesso;
import com.deliveryplatform.catalog.application.port.out.PermissaoDoCatalogo;
import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.application.usecase.ConsultarProdutoService;
import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * A consulta de um produto, sem Spring e sem banco.
 *
 * <p>O mapeamento para o {@code ProdutoResponse} é da borda, e quem o afirma é o
 * {@code ConsultaDeProdutoIT}, contra o JSON de verdade. Aqui só o caso de uso — e,
 * sobretudo, a <b>ordem das etapas</b>, que só um repositório que conta as buscas
 * consegue afirmar de fora.
 */
class ConsultarProdutoServiceTest {

    private static final UUID LOJA_A = ProdutoDeTeste.LOJA;
    private static final UUID LOJA_B = UUID.randomUUID();

    @Test
    @DisplayName("1 · produto da loja autorizada volta inteiro, com os grupos")
    void produto_da_loja_volta_inteiro() {
        Produto pizza = ProdutoDeTeste.margheritaPublicada();
        RepositorioQueConta repo = new RepositorioQueConta().com(pizza);

        Produto lido = servico(comPermissao(PermissaoDoCatalogo.VER_PRODUTO), repo)
                .consultar(LOJA_A, pizza.getId());

        assertThat(lido.getId()).isEqualTo(pizza.getId());
        assertThat(lido.getGruposDeOpcoes()).isNotEmpty();
        assertThat(lido.getGruposDeOpcoes().get(0).opcoes()).hasSize(3);
    }

    @Test
    @DisplayName("2 · sem vínculo com a loja, 403 — e o produto nem é buscado")
    void sem_vinculo_nao_busca() {
        Produto pizza = ProdutoDeTeste.margheritaPublicada();
        RepositorioQueConta repo = new RepositorioQueConta().com(pizza);

        assertThatThrownBy(() -> servico(loja -> Optional.empty(), repo)
                .consultar(LOJA_A, pizza.getId()))
                .isInstanceOf(AcessoNegado.class);
        assertThat(repo.buscas).isZero();
    }

    @Test
    @DisplayName("3 · com vínculo e sem VER_PRODUTO, 403 — e também sem buscar")
    void sem_permissao_nao_busca() {
        Produto pizza = ProdutoDeTeste.margheritaPublicada();
        RepositorioQueConta repo = new RepositorioQueConta().com(pizza);

        assertThatThrownBy(() -> servico(comPermissao(PermissaoDoCatalogo.ALTERAR_PRODUTO), repo)
                .consultar(LOJA_A, pizza.getId()))
                .isInstanceOf(AcessoNegado.class);
        assertThat(repo.buscas).isZero();
    }

    @Test
    @DisplayName("4 · produto que não existe é 404")
    void inexistente_e_404() {
        RepositorioQueConta repo = new RepositorioQueConta();

        assertThatThrownBy(() -> servico(comPermissao(PermissaoDoCatalogo.VER_PRODUTO), repo)
                .consultar(LOJA_A, UUID.randomUUID()))
                .isInstanceOf(ProdutoNaoEncontrado.class);
    }

    @Test
    @DisplayName("5 · produto de outra loja é 404, o mesmo de não existir")
    void de_outra_loja_e_404() {
        Produto daLojaB = Produto.rascunho(LOJA_B, ProdutoDeTeste.CATEGORIA, "Pizza da B",
                Precos.reais("40.00"), ModoDeControle.QUALITATIVO, 0);
        RepositorioQueConta repo = new RepositorioQueConta().com(daLojaB);

        // Autorizado para a A; o repositório devolve o produto da B, porque o
        // buscarPorId não filtra por loja. A conferência é do caso de uso.
        assertThatThrownBy(() -> servico(comPermissao(PermissaoDoCatalogo.VER_PRODUTO), repo)
                .consultar(LOJA_A, daLojaB.getId()))
                .isInstanceOf(ProdutoNaoEncontrado.class);
        assertThat(repo.buscas).isEqualTo(1);
    }

    @Test
    @DisplayName("6 · os dois 404 têm a mesma mensagem — o corpo não desfaz o status")
    void os_dois_404_sao_iguais() {
        Produto daLojaB = Produto.rascunho(LOJA_B, ProdutoDeTeste.CATEGORIA, "Pizza da B",
                Precos.reais("40.00"), ModoDeControle.QUALITATIVO, 0);
        RepositorioQueConta repo = new RepositorioQueConta().com(daLojaB);
        ConsultarProdutoService servico = servico(comPermissao(PermissaoDoCatalogo.VER_PRODUTO), repo);

        Throwable deOutraLoja = catchThrowable(() -> servico.consultar(LOJA_A, daLojaB.getId()));
        Throwable inexistente = catchThrowable(() -> servico.consultar(LOJA_A, UUID.randomUUID()));

        assertThat(deOutraLoja).isInstanceOf(ProdutoNaoEncontrado.class);
        assertThat(inexistente).isInstanceOf(ProdutoNaoEncontrado.class);
        assertThat(deOutraLoja.getMessage()).isEqualTo(inexistente.getMessage());
    }

    @Test
    @DisplayName("7 · rascunho sai, com o estado de publicação dele")
    void rascunho_sai() {
        Produto rascunho = ProdutoDeTeste.margherita();
        RepositorioQueConta repo = new RepositorioQueConta().com(rascunho);

        Produto lido = servico(comPermissao(PermissaoDoCatalogo.VER_PRODUTO), repo)
                .consultar(LOJA_A, rascunho.getId());

        assertThat(lido.getEstadoDePublicacao()).isEqualTo(EstadoDePublicacao.RASCUNHO);
    }

    // ── montagem ────────────────────────────────────────────────────────────

    private static ConsultarProdutoService servico(AutorizacaoComercialPort autorizacao,
                                                   ProdutoRepositorio produtos) {
        return new ConsultarProdutoService(autorizacao, produtos);
    }

    private static AutorizacaoComercialPort comPermissao(PermissaoDoCatalogo permissao) {
        return loja -> Optional.of(new ContextoDeAcesso(UUID.randomUUID(), loja, EnumSet.of(permissao)));
    }

    /**
     * Dublê à mão, porque a porta tem quatro métodos. Ele <b>conta as buscas</b> —
     * é o que afirma a ordem das etapas de fora.
     *
     * <p>Devolve o objeto guardado, e não uma cópia: a consulta não muda o produto.
     * (O da G-C3a precisava de cópia porque a reativação muda e grava; este não
     * grava nada.)
     */
    private static final class RepositorioQueConta implements ProdutoRepositorio {
        private final Map<UUID, Produto> porId = new HashMap<>();
        int buscas = 0;

        RepositorioQueConta com(Produto produto) {
            porId.put(produto.getId(), produto);
            return this;
        }

        @Override
        public Optional<Produto> buscarPorId(UUID id) {
            buscas++;
            return Optional.ofNullable(porId.get(id));
        }

        @Override
        public Produto salvar(Produto produto) {
            throw new UnsupportedOperationException("a consulta não grava");
        }

        @Override
        public Page<Produto> publicadosDe(UUID estabelecimentoId, Pageable paginacao) {
            throw new UnsupportedOperationException("não é deste caso de uso");
        }

        @Override
        public List<UUID> idsParaReativar(UUID estabelecimentoId, LocalDate expediente, int limite) {
            throw new UnsupportedOperationException("não é deste caso de uso");
        }
    }
}
