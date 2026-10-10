import { estadoConhecido, type EstadoDeDisponibilidade } from '../api/disponibilidade';
import type { components } from '../api/generated/catalog';
import { precoLegivel } from './produtos';

/**
 * A ponte entre o produto inteiro do contrato — `GET .../catalog/produtos/{id}`,
 * a rota da G-F — e a tela da opção. Mesmo papel do `produtos.ts` para a lista:
 * nenhum componente lê o contrato direto.
 *
 * ## O que esta ponte não faz: calcular o vendável
 *
 * O `vendavel` vem do servidor e passa intacto. Ele é derivado no `catalog`
 * (`catalogo.md` §4, "Vendabilidade derivada") a partir do estado de publicação,
 * do estado do produto e das opções disponíveis em cada grupo obrigatório — e
 * esta ponte tem todos esses dados na mão. **Recalcular aqui seria um segundo
 * lugar onde a regra está escrita**, e no dia em que ela mudar no servidor a tela
 * passaria a mentir sem nada ficar vermelho. O teste
 * `a opção esgotada aparece, e o vendável é o que o servidor disse` existe para
 * isso.
 */

export type ProdutoInteiroDoContrato = components['schemas']['ProdutoResponse'];
type GrupoDoContrato = components['schemas']['GrupoResponse'];
type OpcaoDoContrato = components['schemas']['OpcaoResponse'];

export type OpcaoNaTela = {
  id: string;
  nome: string;
  /** `null` quando o acréscimo é zero — e aí a tela não mostra nada (W-D). */
  acrescimo: string | null;
  disponibilidade: EstadoDeDisponibilidade | null;
};

export type GrupoNaTela = {
  id: string;
  nome: string;
  /** A frase de quantas escolhas o grupo pede, tirada do `min` e do `max`. */
  regra: string;
  obrigatorio: boolean;
  opcoes: OpcaoNaTela[];
};

export type ProdutoAbertoNaTela = {
  id: string;
  nome: string;
  preco: string;
  vendavel: boolean;
  disponibilidade: EstadoDeDisponibilidade | null;
  grupos: GrupoNaTela[];
};

export function caminhoDoProduto(estabelecimentoId: string, produtoId: string): string {
  return `/api/v1/merchants/${estabelecimentoId}/catalog/produtos/${produtoId}`;
}

export function produtoAbertoParaTela(produto: ProdutoInteiroDoContrato): ProdutoAbertoNaTela {
  return {
    id: String(produto.id ?? ''),
    nome: produto.nome ?? '',
    preco: precoLegivel(produto.precoBase),
    vendavel: produto.vendavel === true,
    disponibilidade: estadoConhecido(produto.disponibilidade),
    grupos: naOrdem(produto.gruposDeOpcoes ?? []).map(grupoParaTela),
  };
}

function grupoParaTela(grupo: GrupoDoContrato): GrupoNaTela {
  const min = grupo.minEscolhas ?? 0;
  const max = grupo.maxEscolhas ?? 0;
  return {
    id: String(grupo.id ?? ''),
    nome: grupo.nome ?? '',
    regra: regraDoGrupo(min, max),
    obrigatorio: min >= 1,
    opcoes: naOrdem(grupo.opcoes ?? []).map(opcaoParaTela),
  };
}

function opcaoParaTela(opcao: OpcaoDoContrato): OpcaoNaTela {
  return {
    id: String(opcao.id ?? ''),
    nome: opcao.nome ?? '',
    acrescimo: acrescimoLegivel(opcao.acrescimo),
    disponibilidade: estadoConhecido(opcao.disponibilidade),
  };
}

/**
 * A ordem que o comerciante escolheu, pelo campo `ordem` — o servidor o manda, e
 * a tela não confia na ordem da lista. É apresentação, não regra.
 */
function naOrdem<T extends { ordem?: number }>(itens: T[]): T[] {
  return [...itens].sort((a, b) => (a.ordem ?? 0) - (b.ordem ?? 0));
}

/**
 * Quantas escolhas o grupo pede, **só** a partir do `min` e do `max` que o
 * servidor mandou. A frase descreve o número; ela não decide nada.
 */
export function regraDoGrupo(min: number, max: number): string {
  if (min === 0) return `Opcional, até ${max}`;
  if (min === max) return `Escolha ${min}`;
  return `Escolha de ${min} a ${max}`;
}

/**
 * O acréscimo com sinal, e **nada** quando é zero (W-D, §6.3 do pacote).
 *
 * `+ R$ 0,00` ao lado de "Pequena" obriga a pessoa a ler para descobrir que não
 * há nada a ler. Positivo e negativo são formatação; o zero que some é escolha,
 * e tem caso de teste próprio.
 *
 * O sinal é o do `Intl` (`signDisplay: 'exceptZero'`), e não montado à mão:
 * em `pt-BR` ele dá `+R$ 8,00` e `-R$ 2,00` — medido na W-D. O negativo existe
 * no domínio: "Sem cebola", −2,00.
 */
const COM_SINAL = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  signDisplay: 'exceptZero',
});

export function acrescimoLegivel(acrescimo: number | undefined): string | null {
  if (acrescimo === undefined || acrescimo === 0) return null;
  return COM_SINAL.format(acrescimo);
}
