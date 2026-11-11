import { estadoConhecido, type EstadoDeDisponibilidade } from '../api/disponibilidade';
import type { components } from '../api/generated/catalog';

/**
 * A **única** ponte entre o contrato do catálogo e a tela: nenhum componente lê
 * o contrato direto.
 *
 * ## A forma do contrato, conferida na W-B
 *
 * A página é `{ conteudo, pagina, tamanho, total }` e o `precoBase` é
 * **number** — é o que o `catalog-service.json` commitado diz. O pacote da
 * rodada supunha `totalDeElementos`, `ultima` e um preço `{ valor, moeda }`;
 * nenhum dos três existe. A correção mora aqui, e só aqui, que é a razão de
 * este arquivo existir.
 */

/** Exportado porque o componente precisa nomeá-lo — sem truque de `Parameters<>`. */
export type PaginaDoContrato = components['schemas']['PaginaResponseProdutoResumoResponse'];

export type ProdutoDoContrato = components['schemas']['ProdutoResumoResponse'];

/** O produto inteiro — o que a publicação devolve. Só os campos do resumo são lidos. */
export type ProdutoInteiroDoContrato = components['schemas']['ProdutoResponse'];

export type EstadoDePublicacao = NonNullable<ProdutoDoContrato['estadoDePublicacao']>;

const PUBLICACAO: Record<EstadoDePublicacao, string> = {
  RASCUNHO: 'Rascunho',
  ATIVO: 'No cardápio',
  INATIVO: 'Fora do cardápio',
};

/**
 * O estado de publicação em português — **num lugar só**, como o pacote da W-F pede.
 * Valor que este front não conhece vira `null` (§4.1 das premissas), e a tela não
 * inventa rótulo para ele.
 */
export function rotuloDaPublicacao(estado: EstadoDePublicacao | null): string | null {
  return estado === null ? null : PUBLICACAO[estado];
}

export function publicacaoConhecida(valor: unknown): EstadoDePublicacao | null {
  return typeof valor === 'string' && valor in PUBLICACAO ? (valor as EstadoDePublicacao) : null;
}

/** O que a tela usa, já achatado — nenhum componente lê o contrato direto. */
export type Produto = {
  id: string;
  nome: string;
  preco: string;
  vendavel: boolean;
  /** `null` quando o servidor mandou um estado que este front não conhece — §4.1. */
  disponibilidade: EstadoDeDisponibilidade | null;
  /** A seção. `null` quando o servidor não mandou — o produto cai em "Sem seção". */
  categoriaId: string | null;
  ordem: number;
  /** Lido, nunca derivado do `vendavel` (ADR-068 §2). `null` se desconhecido. */
  publicacao: EstadoDePublicacao | null;
};

export type PaginaDeProdutos = {
  produtos: Produto[];
  total: number;
  temMais: boolean;
};

export function caminhoDosProdutos(estabelecimentoId: string, tamanho = 20): string {
  return `/api/v1/merchants/${estabelecimentoId}/catalog/produtos?page=0&size=${tamanho}`;
}

export function paraTela(pagina: PaginaDoContrato): PaginaDeProdutos {
  const conteudo = pagina.conteudo ?? [];
  return {
    produtos: conteudo.map(produtoParaTela),
    total: pagina.total ?? conteudo.length,
    // Sem `ultima` no contrato: há mais quando o que já foi mostrado — as
    // páginas anteriores mais esta — não alcança o total.
    temMais:
      pagina.total !== undefined &&
      (pagina.pagina ?? 0) * (pagina.tamanho ?? conteudo.length) + conteudo.length < pagina.total,
  };
}

/** Um produto na forma da tela — também o que a marcação devolve (W-C). */
export function produtoParaTela(produto: ProdutoDoContrato | ProdutoInteiroDoContrato): Produto {
  return {
    id: String(produto.id ?? ''),
    nome: produto.nome ?? '',
    preco: precoLegivel(produto.precoBase),
    vendavel: produto.vendavel === true,
    disponibilidade: estadoConhecido(produto.disponibilidade),
    categoriaId: produto.categoriaId ?? null,
    ordem: produto.ordem ?? 0,
    publicacao: publicacaoConhecida(produto.estadoDePublicacao),
  };
}

/**
 * Formata, e não calcula.
 *
 * <p>O `precoBase` chega como **number**: o `BigDecimal` de escala 2 do servidor
 * (ADR-009) vira número no JSON, e `49.90` sai do `JSON.parse` como `49.9`.
 * Trocar o ponto pela vírgula daria "R$ 49,9". O `Intl.NumberFormat` põe as
 * duas casas e o símbolo — é o que o `premissas-do-front.md` §3 manda — e não
 * soma, não multiplica, não arredonda conta nenhuma: o front exibe o que o
 * servidor calculou.
 */
const REAIS = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export function precoLegivel(preco: number | undefined): string {
  return preco === undefined ? '—' : REAIS.format(preco);
}
