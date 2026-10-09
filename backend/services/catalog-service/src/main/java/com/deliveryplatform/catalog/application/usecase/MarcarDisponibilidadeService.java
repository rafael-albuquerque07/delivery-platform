package com.deliveryplatform.catalog.application.usecase;

import com.deliveryplatform.catalog.application.exception.AcessoNegado;
import com.deliveryplatform.catalog.application.exception.LojaSemExpediente;
import com.deliveryplatform.catalog.application.exception.ProdutoNaoEncontrado;
import com.deliveryplatform.catalog.application.port.in.MarcarDisponibilidade;
import com.deliveryplatform.catalog.application.port.out.AutorizacaoComercialPort;
import com.deliveryplatform.catalog.application.port.out.ContextoDeAcesso;
import com.deliveryplatform.catalog.application.port.out.ExpedienteCorrentePort;
import com.deliveryplatform.catalog.application.port.out.PermissaoDoCatalogo;
import com.deliveryplatform.catalog.application.port.out.ProdutoRepositorio;
import com.deliveryplatform.catalog.domain.model.Disponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.Produto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Autoriza, carimba, marca, grava.
 *
 * <h2>A ordem, e o que cada passo custa</h2>
 *
 * <p>A autorização vem primeiro e é a mais barata — tem cache (ADR-011). Só
 * depois dela se pergunta o expediente, que <b>não</b> tem cache e é uma ida à
 * rede por marcação. Invertido, quem não tem permissão faria o
 * {@code merchant} trabalhar duas vezes para levar 403.
 *
 * <h2>O carimbo, e a loja que não abre por horário</h2>
 *
 * <p>O par {@code marcadoEm} + {@code expedienteDeReferencia} nasce inteiro ou
 * não nasce — o construtor da {@code Disponibilidade} recusa o meio-termo. Numa
 * loja sem horário não há expediente, e aí há dois caminhos:
 *
 * <ul>
 *   <li><b>{@code ESGOTADO_HOJE}</b> é recusado com 409. Sem expediente ele
 *       nunca reativaria, e o produto sumiria do cardápio para sempre sem erro
 *       em lugar nenhum;</li>
 *   <li><b>os outros três estados</b> são aceitos <b>sem carimbo</b>. É legal
 *       pelo domínio — o carimbo só é obrigatório para {@code ESGOTADO_HOJE} —
 *       e é honesto: o carimbo existe para a comparação da reativação, e numa
 *       loja que nunca abre expediente não há comparação a fazer. Gravar um
 *       {@code marcadoEm} sozinho seria inventar a metade que o domínio proíbe.
 * </ul>
 *
 * <h2>Uma transação, e o agregado inteiro</h2>
 *
 * <p>Produto com grupos e opções é uma árvore lida inteira e gravada inteira
 * ({@code catalogo.md} §7). Marcar uma opção passa pela raiz —
 * {@code marcarOpcao} é o único jeito — e grava o produto todo, na transação do
 * {@code MongoTransactionManager} que a G-B1 montou.
 *
 * <h2>O evento que esta rodada não publica</h2>
 *
 * <p>O {@code catalogo.md} §8 manda publicar {@code DisponibilidadeAlteradaV1}
 * em toda mudança de disponibilidade, e diz que ele é "o mais sensível a
 * atraso". <b>Não há outbox no {@code catalog}</b>, e o consumidor previsto —
 * o {@code conversation} — não tem código. Fica adiado com gatilho escrito, e
 * o gatilho é o {@code conversation} ganhar código.
 */
@Service
public class MarcarDisponibilidadeService implements MarcarDisponibilidade {

    private final AutorizacaoComercialPort autorizacao;
    private final ExpedienteCorrentePort expedientes;
    private final ProdutoRepositorio produtos;
    private final Clock relogio;

    public MarcarDisponibilidadeService(AutorizacaoComercialPort autorizacao,
                                        ExpedienteCorrentePort expedientes,
                                        ProdutoRepositorio produtos,
                                        Clock relogio) {
        this.autorizacao = autorizacao;
        this.expedientes = expedientes;
        this.produtos = produtos;
        this.relogio = relogio;
    }

    @Override
    @Transactional
    public Produto deProduto(UUID estabelecimentoId, UUID produtoId,
                             EstadoDeDisponibilidade novo) {
        return marcar(estabelecimentoId, produtoId, novo,
                (alvo) -> alvo.produto.marcar(alvo.nova));
    }

    @Override
    @Transactional
    public Produto deOpcao(UUID estabelecimentoId, UUID produtoId, UUID grupoId, UUID opcaoId,
                           EstadoDeDisponibilidade novo) {
        return marcar(estabelecimentoId, produtoId, novo,
                (alvo) -> alvo.produto.marcarOpcao(grupoId, opcaoId, alvo.nova));
    }

    /**
     * A invariante 9 pela borda certa: o {@code estabelecimentoId} da URL é confrontado
     * com o do produto, e produto de outra loja é <b>404</b> — o mesmo de não existir
     * (ADR-056).
     *
     * <p><b>Até 09/10/2026 isto era 403</b>, com esta razão: "a diferença entre os dois
     * códigos é um varredor de identificadores". A razão está certa e a conclusão não: o
     * que ela exige é que as duas situações respondam <i>igual</i>, e 404 para as duas
     * cumpre isso tanto quanto 403 para as duas. Quem decidiu foi o cliente — com dois
     * sentidos no 403, a tela não consegue saber se pede para recarregar o painel ou se
     * o identificador está errado.
     *
     * <p><b>A ordem é parte da regra:</b> autoriza, busca, confere a loja — e só então
     * pergunta o expediente ao {@code merchant}. Com a conferência depois, um produto de
     * outra loja poderia receber 409 ou 503, respostas sobre uma loja que não é a dele.
     * O {@code MarcarDisponibilidadeServiceTest} tem o caso.
     *
     * <p>(Este javadoc estava, até a G-G, solto acima do {@code exigirPermissao}, onde
     * não documentava nada.)
     */
    private Produto marcar(UUID estabelecimentoId, UUID produtoId,
                           EstadoDeDisponibilidade novo, Consumer<Alvo> ato) {
        exigirPermissao(estabelecimentoId);

        Produto produto = produtos.buscarPorId(produtoId)
                .filter(encontrado -> encontrado.getEstabelecimentoId().equals(estabelecimentoId))
                .orElseThrow(ProdutoNaoEncontrado::new);

        ato.accept(new Alvo(produto, carimbar(estabelecimentoId, novo)));

        return produtos.salvar(produto);
    }

    /** A forma do {@code ListarProdutosService}: sem contexto é 403, sem a permissão também. */
    private void exigirPermissao(UUID estabelecimentoId) {
        ContextoDeAcesso contexto = autorizacao
                .contexto(estabelecimentoId)
                .orElseThrow(AcessoNegado::new);
        if (!contexto.pode(PermissaoDoCatalogo.ALTERAR_PRODUTO)) {
            throw new AcessoNegado();
        }
    }

    private Disponibilidade carimbar(UUID estabelecimentoId, EstadoDeDisponibilidade novo) {
        Optional<LocalDate> expediente = expedientes.de(estabelecimentoId);

        if (expediente.isEmpty()) {
            if (novo == EstadoDeDisponibilidade.ESGOTADO_HOJE) {
                throw new LojaSemExpediente();
            }
            // Sem carimbo, e não meio carimbo: o par nasce inteiro ou não nasce.
            return new Disponibilidade(novo, null, null);
        }

        Instant agora = Instant.now(relogio);
        return new Disponibilidade(novo, agora, expediente.get());
    }

    /** O par que o ato recebe: o agregado e a disponibilidade já carimbada. */
    private record Alvo(Produto produto, Disponibilidade nova) {
    }
}
