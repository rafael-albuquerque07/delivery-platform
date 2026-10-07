import { chamar, ErroDaApi, SemResposta } from './cliente';
import type { components } from './generated/catalog';

/**
 * A marcação de disponibilidade de um produto.
 *
 * `PUT /api/v1/merchants/{estabelecimentoId}/catalog/produtos/{produtoId}/disponibilidade`
 *
 * Módulo sem React de propósito: a tradução de recusa é função pura, e a chamada
 * é uma linha sobre o `chamar`. A tela é o `MarcarDisponibilidade.tsx`.
 *
 * ## O que este módulo não precisou escrever
 *
 * O pacote da rodada trazia uma interface `ProblemDetail` escrita à mão. Ela não
 * é necessária: desde a W-A o `cliente.ts` lê o `detail` (`lerDetalhe`) — só
 * quando o corpo é JSON e o campo é texto não vazio — e o entrega em
 * `ErroDaApi.detalhe`. O corpo de erro continua sem tipo gerado (o contrato o
 * declara sem corpo, G-E), mas quem o lê é um lugar só, e já existia.
 */

/** O estado como o contrato o declara — e é dele que sai a lista. */
export type EstadoDeDisponibilidade = components['schemas']['MarcacaoRequest']['estado'];

/**
 * Os quatro, na ordem da tela. O `Record` do {@link ROTULO} é o que prende esta
 * lista ao contrato: se o servidor ganhar um quinto estado e os tipos forem
 * regerados, o `typecheck` cai aqui — e é de propósito (ADR-027 §2).
 */
export const ESTADOS: readonly EstadoDeDisponibilidade[] = [
  'DISPONIVEL',
  'ACABANDO',
  'ESGOTADO_HOJE',
  'ESGOTADO_INDETERMINADO',
];

export const ROTULO: Record<EstadoDeDisponibilidade, string> = {
  DISPONIVEL: 'Disponível',
  ACABANDO: 'Acabando',
  ESGOTADO_HOJE: 'Acabou hoje',
  ESGOTADO_INDETERMINADO: 'Acabou, sem previsão',
};

/**
 * Lê o estado que veio do servidor, e **nunca concede nada** ao que não conhece
 * (`premissas-do-front.md` §4.1): valor desconhecido vira `null`, que a tela
 * mostra como neutro — nunca como "disponível".
 */
export function estadoConhecido(valor: unknown): EstadoDeDisponibilidade | null {
  return (ESTADOS as readonly unknown[]).includes(valor)
    ? (valor as EstadoDeDisponibilidade)
    : null;
}

/** O que a tela precisa saber para decidir o que mostrar e o que oferecer. */
export interface Recusa {
  /** O código HTTP. `0` quando não houve resposta. */
  status: number;
  /** O texto do servidor, quando houver. Nunca interpretado — ADR-055 §2. */
  detail: string | null;
  /** Mensagem do cliente, usada quando `detail` não veio. */
  mensagem: string;
  /** Recarregar resolve, ou pelo menos não atrapalha. */
  ofereceRecarregar: boolean;
  /** O contexto do painel pode estar velho: permissão mudou debaixo da tela. */
  contextoVelho: boolean;
}

export class MarcacaoRecusada extends Error {
  readonly recusa: Recusa;

  constructor(recusa: Recusa) {
    super(recusa.detail ?? recusa.mensagem);
    this.name = 'MarcacaoRecusada';
    this.recusa = recusa;
  }
}

const MENSAGEM_POR_CODIGO: Record<number, string> = {
  0: 'Não foi possível falar com o servidor.',
  400: 'Este produto não aceita esse estado.',
  403: 'Você não tem mais permissão para alterar este produto.',
  409: 'O produto mudou enquanto você marcava.',
  503: 'Não foi possível consultar o expediente da loja agora.',
};

const MENSAGEM_PADRAO = 'Não foi possível marcar a disponibilidade.';

/**
 * Transforma o que chegou numa {@link Recusa}.
 *
 * O `detalhe` já chega filtrado pelo `cliente.ts` — só texto vindo de um corpo
 * JSON. Aqui falta uma coisa: **texto em branco não é texto** (ADR-055 §3), e o
 * `lerDetalhe` aceita qualquer string não vazia, inclusive `"   "`.
 *
 * **O 403 é o único em que o texto do servidor não vai à tela**: ele é fixo e
 * genérico de propósito (M7, `estabelecimento.md`), então mostrá-lo não ajuda
 * ninguém. A tela pede para recarregar o contexto.
 */
export function recusaDe(status: number, detalhe: string | undefined): Recusa {
  const contextoVelho = status === 403;
  return {
    status,
    detail: contextoVelho || detalhe === undefined || detalhe.trim() === '' ? null : detalhe,
    mensagem: MENSAGEM_POR_CODIGO[status] ?? MENSAGEM_PADRAO,
    // 409: conflito de versão se resolve recarregando, e na loja sem horário
    // recarregar não atrapalha — ADR-055 §2, a desambiguação vem do pedido.
    // 503 e sem resposta: o outro lado pode ter voltado.
    ofereceRecarregar: status === 409 || status === 503 || status === 0,
    contextoVelho,
  };
}

export type ProdutoDoContrato = components['schemas']['ProdutoResumoResponse'];

/**
 * Marca, e devolve **o produto inteiro que o servidor recalculou** — no formato
 * do contrato; quem o põe na forma da tela é o `painel/produtos.ts`, a ponte de
 * sempre.
 *
 * Não há atualização otimista, e isso é decisão (G-C2, ADR-055): marcar pode
 * derrubar o `vendavel`, e o cliente não consegue derivá-lo — o resumo não
 * carrega os grupos. A resposta é a verdade, e a tela espera.
 *
 * A recusa é traduzida **aqui, onde a exceção nasce**. O 401 passa intacto: o
 * `chamar` já derrubou a sessão (W-A), e a raiz da aplicação é quem reage.
 */
export async function marcarDisponibilidade(
  estabelecimentoId: string,
  produtoId: string,
  estado: EstadoDeDisponibilidade,
  token: string,
): Promise<ProdutoDoContrato> {
  const caminho =
    `/api/v1/merchants/${estabelecimentoId}` + `/catalog/produtos/${produtoId}/disponibilidade`;

  try {
    return await chamar<ProdutoDoContrato>(caminho, { token, metodo: 'PUT', corpo: { estado } });
  } catch (erro) {
    if (erro instanceof ErroDaApi && erro.status !== 401) {
      throw new MarcacaoRecusada(recusaDe(erro.status, erro.detalhe));
    }
    if (erro instanceof SemResposta) {
      throw new MarcacaoRecusada(recusaDe(0, undefined));
    }
    throw erro;
  }
}
