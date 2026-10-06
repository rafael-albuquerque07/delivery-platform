package com.deliveryplatform.catalog.unit;

import com.deliveryplatform.catalog.application.exception.AcessoNegado;
import com.deliveryplatform.catalog.application.exception.LojaSemExpediente;
import com.deliveryplatform.catalog.application.port.out.AutorizacaoComercialPort;
import com.deliveryplatform.catalog.application.port.out.ContextoDeAcesso;
import com.deliveryplatform.catalog.application.port.out.ExpedienteCorrentePort;
import com.deliveryplatform.catalog.application.port.out.ExpedienteIndisponivel;
import com.deliveryplatform.catalog.application.port.out.PermissaoDoCatalogo;
import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.application.usecase.MarcarDisponibilidadeService;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.support.Precos;
import com.deliveryplatform.catalog.support.ProdutoDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O caso de uso sozinho — e as asserções aqui descrevem o que ele <b>deve</b>
 * fazer, não o que ele faz.
 *
 * <p>A distinção não é retórica. Na W-B eu escrevi três asserções que fixavam
 * um defeito: o hook chamava a API sem token, e os testes conferiam exatamente
 * essa chamada. Eles podiam falhar — falhariam se alguém <i>consertasse</i> o
 * código. É o décimo primeiro defeito de método deste projeto, e ele nasce de
 * escrever teste e código na mesma passada, a partir do mesmo modelo errado.
 *
 * <p>A defesa, aqui, é a mesma do resto do repositório: <b>o APLICAR §5 manda
 * estragar o código de propósito e medir quem fica vermelho.</b> Cada caso
 * abaixo diz, na mensagem, qual mutação ele existe para pegar.
 *
 * <p><b>Relógio fixo</b>, e por isso o carimbo é afirmável. Um teste de carimbo
 * que chame {@code Instant.now()} afirma o que ele mesmo produziu.
 */
class MarcarDisponibilidadeServiceTest {

    private static final UUID PIZZARIA = UUID.randomUUID();
    private static final UUID OUTRA_LOJA = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-10-01T23:00:00Z");
    private static final LocalDate EXPEDIENTE = LocalDate.parse("2026-10-01");

    // ── a permissão, e o que ela protege ────────────────────────────────────

    @Test
    @DisplayName("sem ALTERAR_PRODUTO é 403 — e o merchant nem é perguntado sobre o expediente")
    void sem_permissao_nao_pergunta_expediente() {
        // O produto EXISTE e é desta loja, de propósito: se a autorização fosse
        // pulada, a busca encontraria e o dublê do expediente estouraria
        // ExpedienteIndisponivel — outro tipo. É isso que faz esta asserção
        // distinguir "recusou por permissão" de "recusou por acaso".
        Produto produto = produtoSimples();
        var servico = servico(
                autorizacaoCom(PermissaoDoCatalogo.VER_PRODUTO),
                expedienteQueEstoura(),
                repositorioCom(produto));

        assertThatThrownBy(() -> servico.deProduto(
                PIZZARIA, produto.getId(), EstadoDeDisponibilidade.ACABANDO))
                .as("a autorização tem cache e o expediente não tem; perguntar o "
                        + "expediente antes faz o merchant trabalhar à toa para "
                        + "devolver 403")
                .isInstanceOf(AcessoNegado.class);
    }

    @Test
    @DisplayName("produto de outra loja é 403, e não 404 — a diferença varreria identificadores")
    void produto_de_outra_loja() {
        Produto deOutraLoja = produtoDe(OUTRA_LOJA);

        var servico = servico(
                autorizacaoCom(PermissaoDoCatalogo.ALTERAR_PRODUTO),
                expedienteDe(EXPEDIENTE),
                repositorioCom(deOutraLoja));

        assertThatThrownBy(() -> servico.deProduto(
                PIZZARIA, deOutraLoja.getId(), EstadoDeDisponibilidade.ACABANDO))
                .as("invariante 9: o identificador da URL é confrontado com o do "
                        + "produto. Sem o confronto, quem tem ALTERAR_PRODUTO numa "
                        + "loja marcaria o cardápio de qualquer outra")
                .isInstanceOf(AcessoNegado.class);
    }

    // ── o carimbo ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("o carimbo é o relógio do serviço e o expediente do merchant — nenhum vem do pedido")
    void carimba_com_o_que_o_servidor_sabe() {
        Produto produto = produtoSimples();
        var servico = servico(
                autorizacaoCom(PermissaoDoCatalogo.ALTERAR_PRODUTO),
                expedienteDe(EXPEDIENTE),
                repositorioCom(produto));

        Produto salvo = servico.deProduto(
                PIZZARIA, produto.getId(), EstadoDeDisponibilidade.ESGOTADO_HOJE);

        assertThat(salvo.getDisponibilidade().estado())
                .isEqualTo(EstadoDeDisponibilidade.ESGOTADO_HOJE);
        assertThat(salvo.getDisponibilidade().marcadoEm()).isEqualTo(AGORA);
        assertThat(salvo.getDisponibilidade().expedienteDeReferencia())
                .as("se alguém trocar a porta por LocalDate.now(), este caso fica "
                        + "vermelho — 01/10 às 23:00 UTC é 02/10 em boa parte do mundo, "
                        + "e o expediente da loja não é o dia do relógio")
                .isEqualTo(EXPEDIENTE);
    }

    @Test
    @DisplayName("o serviço devolve o que o repositório gravou, e não o objeto que ele tinha em mão")
    void devolve_o_salvo() {
        Produto produto = produtoSimples();
        Produto oQueOBancoDevolveu = produtoSimples();

        var servico = new MarcarDisponibilidadeService(
                autorizacaoCom(PermissaoDoCatalogo.ALTERAR_PRODUTO),
                expedienteDe(EXPEDIENTE),
                new ProdutoRepositorio() {
                    @Override
                    public Produto salvar(Produto p) {
                        return oQueOBancoDevolveu;
                    }

                    @Override
                    public Optional<Produto> buscarPorId(UUID id) {
                        return Optional.of(produto);
                    }

                    @Override
                    public org.springframework.data.domain.Page<Produto> publicadosDe(
                            UUID estabelecimentoId,
                            org.springframework.data.domain.Pageable paginacao) {
                        throw new UnsupportedOperationException("não é deste caso de uso");
                    }
                },
                Clock.fixed(AGORA, ZoneOffset.UTC));

        assertThat(servico.deProduto(PIZZARIA, produto.getId(), EstadoDeDisponibilidade.ACABANDO))
                .as("o adaptador do Mongo mapeia na volta; devolver o objeto da "
                        + "memória esconderia qualquer coisa que a gravação faça")
                .isSameAs(oQueOBancoDevolveu);
    }

    // ── a loja que não abre por horário ─────────────────────────────────────

    @Test
    @DisplayName("ESGOTADO_HOJE numa loja sem horário é 409 — ele nunca reativaria")
    void esgotado_hoje_sem_expediente_e_recusado() {
        Produto produto = produtoSimples();
        var servico = servico(
                autorizacaoCom(PermissaoDoCatalogo.ALTERAR_PRODUTO),
                semExpediente(),
                repositorioCom(produto));

        assertThatThrownBy(() -> servico.deProduto(
                PIZZARIA, produto.getId(), EstadoDeDisponibilidade.ESGOTADO_HOJE))
                .as("'volta na abertura do próximo expediente' numa loja que não "
                        + "abre por horário é 'não volta nunca', e em silêncio")
                .isInstanceOf(LojaSemExpediente.class);
    }

    @Test
    @DisplayName("ESGOTADO_INDETERMINADO numa loja sem horário é aceito, e sem carimbo")
    void sem_expediente_os_outros_estados_passam() {
        Produto produto = produtoSimples();
        var servico = servico(
                autorizacaoCom(PermissaoDoCatalogo.ALTERAR_PRODUTO),
                semExpediente(),
                repositorioCom(produto));

        Produto salvo = servico.deProduto(
                PIZZARIA, produto.getId(), EstadoDeDisponibilidade.ESGOTADO_INDETERMINADO);

        assertThat(salvo.getDisponibilidade().estado())
                .isEqualTo(EstadoDeDisponibilidade.ESGOTADO_INDETERMINADO);
        assertThat(salvo.getDisponibilidade().marcadoEm())
                .as("o par nasce inteiro ou não nasce. Gravar só o instante seria "
                        + "a metade que o construtor da Disponibilidade recusa — e "
                        + "se alguém 'consertar' pondo marcadoEm aqui, estoura")
                .isNull();
        assertThat(salvo.getDisponibilidade().expedienteDeReferencia()).isNull();
    }

    @Test
    @DisplayName("merchant fora do ar não vira marcação sem carimbo — falha fechada")
    void expediente_indisponivel_propaga() {
        Produto produto = produtoSimples();
        var servico = servico(
                autorizacaoCom(PermissaoDoCatalogo.ALTERAR_PRODUTO),
                expedienteQueEstoura(),
                repositorioCom(produto));

        assertThatThrownBy(() -> servico.deProduto(
                PIZZARIA, produto.getId(), EstadoDeDisponibilidade.ACABANDO))
                .as("este é o caso que separa 'a loja não abre' de 'o merchant caiu'. "
                        + "Se o adaptador traduzir 5xx em Optional.empty(), uma queda "
                        + "vira carimbo ausente gravado no banco, para sempre")
                .isInstanceOf(ExpedienteIndisponivel.class);
    }

    // ── a opção, e a cláusula que ninguém lembra ────────────────────────────

    @Test
    @DisplayName("marcar a última opção disponível de um grupo obrigatório derruba o vendável")
    void a_terceira_clausula_do_vendavel() {
        Produto pizza = produtoComTamanhoObrigatorio();
        GrupoDeOpcoes tamanho = pizza.getGruposDeOpcoes().getFirst();

        var servico = servico(
                autorizacaoCom(PermissaoDoCatalogo.ALTERAR_PRODUTO),
                expedienteDe(EXPEDIENTE),
                repositorioCom(pizza));

        Produto salvo = servico.deOpcao(PIZZARIA, pizza.getId(), tamanho.id(),
                tamanho.opcoes().getFirst().id(), EstadoDeDisponibilidade.ESGOTADO_HOJE);

        assertThat(salvo.vendavel())
                .as("é a razão de a resposta trazer o produto inteiro: a tela não "
                        + "consegue derivar isto, porque o resumo não carrega os grupos")
                .isFalse();
        assertThat(salvo.getDisponibilidade().permiteVenda())
                .as("o PRODUTO continua disponível — quem acabou foi a opção")
                .isTrue();
    }

    @Test
    @DisplayName("opção de outro grupo, ou id que não existe, estoura em vez de reportar sucesso")
    void id_de_opcao_desconhecido() {
        Produto pizza = produtoComTamanhoObrigatorio();
        var servico = servico(
                autorizacaoCom(PermissaoDoCatalogo.ALTERAR_PRODUTO),
                expedienteDe(EXPEDIENTE),
                repositorioCom(pizza));

        assertThatThrownBy(() -> servico.deOpcao(PIZZARIA, pizza.getId(),
                pizza.getGruposDeOpcoes().getFirst().id(), UUID.randomUUID(),
                EstadoDeDisponibilidade.ESGOTADO_HOJE))
                .as("engolir em silêncio faria a tela reportar sucesso sobre um "
                        + "sabor que continua à venda — o javadoc do marcarOpcao "
                        + "diz isso com todas as letras")
                .isInstanceOf(RuntimeException.class);
    }

    // ── os dublês ───────────────────────────────────────────────────────────

    private MarcarDisponibilidadeService servico(AutorizacaoComercialPort autorizacao,
                                                 ExpedienteCorrentePort expedientes,
                                                 ProdutoRepositorio produtos) {
        return new MarcarDisponibilidadeService(
                autorizacao, expedientes, produtos, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static AutorizacaoComercialPort autorizacaoCom(PermissaoDoCatalogo... permissoes) {
        Set<PermissaoDoCatalogo> concedidas = permissoes.length == 0
                ? EnumSet.noneOf(PermissaoDoCatalogo.class)
                : EnumSet.copyOf(List.of(permissoes));
        return loja -> Optional.of(new ContextoDeAcesso(UUID.randomUUID(), loja, concedidas));
    }

    private static ExpedienteCorrentePort expedienteDe(LocalDate dia) {
        return loja -> Optional.of(dia);
    }

    /** A loja que não abre por horário: resposta, e não falha. */
    private static ExpedienteCorrentePort semExpediente() {
        return loja -> Optional.empty();
    }

    private static ExpedienteCorrentePort expedienteQueEstoura() {
        return loja -> {
            throw new ExpedienteIndisponivel("o merchant não respondeu");
        };
    }

    private static ProdutoRepositorio repositorioCom(Produto produto) {
        return new ProdutoRepositorio() {
            @Override
            public Produto salvar(Produto p) {
                return p;
            }

            @Override
            public Optional<Produto> buscarPorId(UUID id) {
                return id.equals(produto.getId()) ? Optional.of(produto) : Optional.empty();
            }

            @Override
            public org.springframework.data.domain.Page<Produto> publicadosDe(
                    UUID estabelecimentoId,
                    org.springframework.data.domain.Pageable paginacao) {
                throw new UnsupportedOperationException("não é deste caso de uso");
            }
        };
    }

    // ── montagem ────────────────────────────────────────────────────────────

    /**
     * Nenhum caso acima depende da forma de construir, só do estado resultante
     * — e é por isso que a montagem mora toda neste bloco.
     */
    private static Produto produtoSimples() {
        return produtoDe(PIZZARIA);
    }

    /**
     * Uma margherita publicada, na loja pedida. O {@code ProdutoDeTeste} só
     * sabe a {@code LOJA} fixa dele, e este teste precisa de duas.
     */
    private static Produto produtoDe(UUID loja) {
        Produto p = Produto.rascunho(loja, ProdutoDeTeste.CATEGORIA, "Pizza margherita",
                Precos.reais("49.90"), ModoDeControle.QUALITATIVO, 0);
        p.acrescentarGrupo(ProdutoDeTeste.tamanho(3));
        p.publicar();
        return p;
    }

    /** Pizza com "Tamanho" obrigatório e <b>uma</b> opção: esgotá-la derruba o vendável. */
    private static Produto produtoComTamanhoObrigatorio() {
        Produto pizza = produtoDe(PIZZARIA);
        pizza.substituirGrupos(List.of(GrupoDeOpcoes.novo(
                "Tamanho", 1, 1, 1,
                List.of(Opcao.nova("Grande", Precos.reais("0.00"), 1)))));
        return pizza;
    }
}
