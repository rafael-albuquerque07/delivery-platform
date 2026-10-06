package com.deliveryplatform.catalog.application.port.out;

import com.deliveryplatform.catalog.domain.model.Produto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A porta de saída do produto. Quatro métodos, e nenhum a mais — o quarto,
 * {@link #idsParaReativar}, chegou na G-C3a; quem acrescentar o quinto emenda
 * esta frase.
 *
 * <p>Não há {@code apagar}: a C9 diz que produto nunca é apagado, e a forma
 * mais barata de garantir isso é não existir o método. Produto que sai do
 * cardápio vai a {@code INATIVO} — o pedido antigo continua reimprimível
 * (ADR-018), e um produto apagado tornaria um pedido de junho ilegível em
 * julho.
 *
 * <p>Não há {@code atualizar} separado de {@code salvar}: a raiz é gravada
 * inteira, que é o que a §7 quer dizer com <i>"árvore lida inteira, gravada
 * inteira"</i>. Gravar pedaço de agregado é como um invariante do agregado
 * deixa de valer sem ninguém perceber.
 */
public interface ProdutoRepositorio {

    Produto salvar(Produto produto);

    Optional<Produto> buscarPorId(UUID id);

    /**
     * Os produtos publicados de uma loja — a consulta que o primeiro índice da
     * §7 existe para servir.
     *
     * <p>Devolve os {@code ATIVO}, e não os <i>vendáveis</i>: vendável é
     * derivado (§5) e não é campo, então filtrar por ele é trabalho de quem
     * chamou, sobre o que voltou daqui.
     *
     * <p><b>Ganhou {@code Pageable} na G-B3, e devia ter nascido com ele.</b> O
     * {@code CLAUDE.md} diz <i>"Paginação obrigatória em toda listagem. Nada de
     * {@code findAll()} sem {@code Pageable}"</i>. Na G-B1 este método nasceu
     * devolvendo {@code List} — antes de existir qualquer listagem por HTTP, o
     * que escondeu a violação — e a primeira rota que o chama é a desta rodada.
     *
     * <p>O tipo do Spring Data está nesta camada de propósito: o ArchUnit
     * protege o {@code domain}, não o {@code application}, e todo adaptador
     * desta porta é Spring Data de qualquer maneira.
     */
    Page<Produto> publicadosDe(UUID estabelecimentoId, Pageable paginacao);

    /**
     * Os produtos da loja que têm algo a reativar: o próprio produto, ou qualquer
     * opção de qualquer grupo, com {@code ESGOTADO_HOJE} e carimbo <b>anterior</b> ao
     * expediente que abriu.
     *
     * <p>Devolve <b>identificadores</b>, não agregados: quem relê cada produto é a
     * unidade de trabalho, na transação dela, e a versão que vale é a de lá
     * (ADR-052).
     *
     * <p>O filtro espelha o {@code Disponibilidade.deveReativarNoExpediente}, e o
     * predicado tem a palavra final.
     *
     * @param limite teto do lote — não é paginação: a varredura pede de novo até
     *               vir vazio, porque o que foi reativado deixa de casar
     */
    List<UUID> idsParaReativar(UUID estabelecimentoId, LocalDate expediente, int limite);
}
