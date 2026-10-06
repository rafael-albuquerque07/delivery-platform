package com.deliveryplatform.catalog.infrastructure.persistence.mapper;

import com.deliveryplatform.catalog.domain.model.Disponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.EstadoDePublicacao;
import com.deliveryplatform.catalog.domain.model.GrupoDeOpcoes;
import com.deliveryplatform.catalog.domain.model.ModoDeControle;
import com.deliveryplatform.catalog.domain.model.Opcao;
import com.deliveryplatform.catalog.domain.model.Produto;
import com.deliveryplatform.catalog.infrastructure.persistence.entity.ProdutoDocumento;
import com.deliveryplatform.catalog.infrastructure.persistence.entity.ProdutoDocumento.DinheiroDocumento;
import com.deliveryplatform.catalog.infrastructure.persistence.entity.ProdutoDocumento.DisponibilidadeDocumento;
import com.deliveryplatform.catalog.infrastructure.persistence.entity.ProdutoDocumento.GrupoDocumento;
import com.deliveryplatform.catalog.infrastructure.persistence.entity.ProdutoDocumento.OpcaoDocumento;
import com.deliveryplatform.valuetypes.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A tradução entre o agregado e o documento, nos dois sentidos.
 *
 * <p>Escrito à mão, e não com MapStruct, por um motivo só: as três conversões
 * que importam — dinheiro, dia operacional e instante — são <b>decisões</b>
 * (ver o javadoc do {@link ProdutoDocumento}), e decisão escondida em
 * anotação é decisão que ninguém relê.
 *
 * <p><b>Nenhuma leitura devolve {@code null} por valor desconhecido.</b> Um
 * enum que o documento traz e o código não conhece é documento de uma versão
 * mais nova, ou documento corrompido; as duas coisas merecem estourar aqui,
 * com o valor na mensagem, e não virar um {@code null} que aparece três
 * camadas adiante.
 */
public final class ProdutoMapper {

    private ProdutoMapper() {
    }

    // ── domínio → documento ─────────────────────────────────────────────────

    public static ProdutoDocumento paraDocumento(Produto produto) {
        return new ProdutoDocumento(
                produto.getId(),
                produto.getEstabelecimentoId(),
                produto.getCategoriaId(),
                produto.getNome(),
                produto.getDescricao(),
                produto.getImagemRef(),
                dinheiro(produto.getPrecoBase()),
                produto.getOrdem(),
                produto.getEstadoDePublicacao().name(),
                produto.getModoDeControle().name(),
                disponibilidade(produto.getDisponibilidade()),
                produto.getGruposDeOpcoes().stream().map(ProdutoMapper::grupo).toList(),
                produto.getVersao());
    }

    private static GrupoDocumento grupo(GrupoDeOpcoes g) {
        return new GrupoDocumento(g.id(), g.nome(), g.minEscolhas(), g.maxEscolhas(), g.ordem(),
                g.opcoes().stream().map(ProdutoMapper::opcao).toList());
    }

    private static OpcaoDocumento opcao(Opcao o) {
        return new OpcaoDocumento(o.id(), o.nome(), dinheiro(o.acrescimo()),
                disponibilidade(o.disponibilidade()), o.ordem());
    }

    private static DinheiroDocumento dinheiro(Money money) {
        return new DinheiroDocumento(
                money.valor().toPlainString(), money.moeda().getCurrencyCode());
    }

    private static DisponibilidadeDocumento disponibilidade(Disponibilidade d) {
        return new DisponibilidadeDocumento(
                d.estado().name(),
                d.marcadoEm() == null ? null : d.marcadoEm().toString(),
                d.expedienteDeReferencia() == null ? null : d.expedienteDeReferencia().toString());
    }

    // ── documento → domínio ─────────────────────────────────────────────────

    public static Produto paraDominio(ProdutoDocumento doc) {
        return Produto.reconstituir(
                doc.id(),
                doc.estabelecimentoId(),
                doc.categoriaId(),
                doc.nome(),
                doc.descricao(),
                doc.imagemRef(),
                money(doc.precoBase(), "precoBase"),
                doc.ordem(),
                enumDe(EstadoDePublicacao.class, doc.estadoDePublicacao(), "estadoDePublicacao"),
                enumDe(ModoDeControle.class, doc.modoDeControle(), "modoDeControle"),
                disponibilidadeDe(doc.disponibilidade()),
                doc.gruposDeOpcoes() == null
                        ? List.of()
                        : doc.gruposDeOpcoes().stream().map(ProdutoMapper::grupoDe).toList(),
                doc.versao());
    }

    private static GrupoDeOpcoes grupoDe(GrupoDocumento g) {
        return new GrupoDeOpcoes(g.id(), g.nome(), g.minEscolhas(), g.maxEscolhas(), g.ordem(),
                g.opcoes() == null
                        ? List.of()
                        : g.opcoes().stream().map(ProdutoMapper::opcaoDe).toList());
    }

    private static Opcao opcaoDe(OpcaoDocumento o) {
        return new Opcao(o.id(), o.nome(), money(o.acrescimo(), "acrescimo"),
                disponibilidadeDe(o.disponibilidade()), o.ordem());
    }

    /**
     * <b>Único ponto desta rodada que toca a API do {@link Money}</b>, junto
     * com o {@code dinheiro(...)} logo acima.
     *
     * <p>A moeda é gravada e <b>conferida</b>, não usada para construir: o
     * {@code Money.de(BigDecimal)} decide a moeda dele, e se um documento
     * disser outra coisa isso é divergência, não conversão. Calar aqui seria
     * ler um preço em dólar como se fosse em real.
     */
    private static Money money(DinheiroDocumento d, String campo) {
        if (d == null) {
            throw new DocumentoIlegivel(campo + " ausente no documento");
        }
        Money lido = Money.de(new BigDecimal(d.valor()));
        if (!lido.moeda().getCurrencyCode().equals(d.moeda())) {
            throw new DocumentoIlegivel(
                    campo + " gravado em " + d.moeda() + ", e este serviço lê em "
                            + lido.moeda().getCurrencyCode());
        }
        return lido;
    }

    private static Disponibilidade disponibilidadeDe(DisponibilidadeDocumento d) {
        if (d == null) {
            throw new DocumentoIlegivel("disponibilidade ausente no documento");
        }
        return new Disponibilidade(
                enumDe(EstadoDeDisponibilidade.class, d.estado(), "disponibilidade.estado"),
                d.marcadoEm() == null ? null : Instant.parse(d.marcadoEm()),
                d.expedienteDeReferencia() == null
                        ? null
                        : LocalDate.parse(d.expedienteDeReferencia()));
    }

    private static <E extends Enum<E>> E enumDe(Class<E> tipo, String valor, String campo) {
        if (valor == null) {
            throw new DocumentoIlegivel(campo + " ausente no documento");
        }
        try {
            return Enum.valueOf(tipo, valor);
        } catch (IllegalArgumentException e) {
            throw new DocumentoIlegivel(
                    campo + " tem o valor '" + valor + "', que este código não conhece — "
                            + "documento de uma versão mais nova, ou corrompido");
        }
    }

    /**
     * Erro de leitura, e não regra de negócio violada.
     *
     * <p>Fica separada da {@code RegraDoCatalogoViolada} de propósito: uma diz
     * que o comerciante pediu algo inválido, a outra diz que o <b>banco</b>
     * tem algo que o código não entende. Quem traduzir isso em HTTP não deve
     * responder 400 para a segunda.
     */
    public static class DocumentoIlegivel extends RuntimeException {
        public DocumentoIlegivel(String mensagem) {
            super(mensagem);
        }
    }
}
