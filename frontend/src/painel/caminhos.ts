import type { Secao } from './secoes';

/**
 * Os endereços do painel (ADR-061), num lugar só: a loja escolhida é um segmento da
 * URL, e cada tela tem endereço. Quem monta link ou navega pede aqui, e ninguém
 * escreve `/painel/lojas/...` à mão.
 */

export const NOVA_LOJA = '/painel/lojas/nova';

/** O segmento de cada seção. A seção mora na URL — não há mais estado que a escolha. */
const SEGMENTO: Record<Secao, string> = {
  cardapio: 'produtos',
  disponibilidade: 'disponibilidade',
  equipe: 'equipe',
  configuracao: 'configuracao',
};

export function caminhoDaLoja(estabelecimentoId: string): string {
  return `/painel/lojas/${estabelecimentoId}`;
}

export function caminhoDaSecao(estabelecimentoId: string, secao: Secao): string {
  return `${caminhoDaLoja(estabelecimentoId)}/${SEGMENTO[secao]}`;
}

/** O formulário do produto novo (ADR-067 §3). */
export function caminhoDoNovoProduto(estabelecimentoId: string): string {
  return `${caminhoDaSecao(estabelecimentoId, 'cardapio')}/novo`;
}

/** A seção em que a URL está, ou `null` — é ela que o menu marca como atual. */
export function secaoDoCaminho(caminho: string): Secao | null {
  const segmento = caminho.split('/')[4];
  const achada = (Object.keys(SEGMENTO) as Secao[]).find((secao) => SEGMENTO[secao] === segmento);
  return achada ?? null;
}
