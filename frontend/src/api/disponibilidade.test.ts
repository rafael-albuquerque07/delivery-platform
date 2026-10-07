import { afterEach, describe, expect, it, vi, type Mock } from 'vitest';

import { ErroDaApi, SemResposta } from './cliente';
import {
  ESTADOS,
  MarcacaoRecusada,
  ROTULO,
  estadoConhecido,
  marcarDisponibilidade,
  recusaDe,
} from './disponibilidade';

vi.mock('./cliente', async () => {
  const real = await vi.importActual<typeof import('./cliente')>('./cliente');
  return { ...real, chamar: vi.fn() };
});

// Mesmo elenco do `ListaDeProdutos.test.tsx`: `chamar` é genérica.
const { chamar } = await import('./cliente');
const chamarFalso = chamar as unknown as Mock;

afterEach(() => {
  chamarFalso.mockReset();
});

/**
 * A tradução de recusa e a chamada, sem React e sem rede.
 *
 * O filtro do corpo — só `detail` textual de um JSON vira `detalhe` — é do
 * `cliente.ts`, e os casos dele estão no `cliente.test.ts`. Aqui, o que chega já
 * é texto ou nada.
 */
describe('recusaDe', () => {
  it('aproveita o detail do servidor', () => {
    const r = recusaDe(409, 'A loja não abre por horário. Use ESGOTADO_INDETERMINADO.');

    expect(r.detail).toBe('A loja não abre por horário. Use ESGOTADO_INDETERMINADO.');
    expect(r.ofereceRecarregar).toBe(true);
    expect(r.contextoVelho).toBe(false);
  });

  it('cai na mensagem por código quando não vem detail', () => {
    const r = recusaDe(503, undefined);

    expect(r.detail).toBeNull();
    expect(r.mensagem).toContain('expediente');
    expect(r.ofereceRecarregar).toBe(true);
  });

  // O `lerDetalhe` aceita qualquer string não vazia; em branco não é texto.
  it('detail em branco não é detail', () => {
    expect(recusaDe(409, '   ').detail).toBeNull();
  });

  // ADR-055: o 403 é o único em que o texto do servidor não vai à tela, porque
  // ele é fixo e genérico de propósito (M7).
  it('no 403 descarta o detail e marca o contexto como velho', () => {
    const r = recusaDe(403, 'sem acesso a este estabelecimento');

    expect(r.detail).toBeNull();
    expect(r.contextoVelho).toBe(true);
    expect(r.ofereceRecarregar).toBe(false);
  });

  it('o 400 não oferece recarregar, porque recarregar não muda nada', () => {
    const r = recusaDe(400, 'produto SEM_CONTROLE não acaba');

    expect(r.ofereceRecarregar).toBe(false);
    expect(r.detail).toBe('produto SEM_CONTROLE não acaba');
  });

  it('sem resposta (0) tem mensagem própria e oferece tentar de novo', () => {
    const r = recusaDe(0, undefined);

    expect(r.mensagem).toMatch(/falar com o servidor/);
    expect(r.ofereceRecarregar).toBe(true);
  });

  it('código que o contrato não declara ainda produz uma recusa utilizável', () => {
    const r = recusaDe(418, undefined);

    expect(r.mensagem).toBe('Não foi possível marcar a disponibilidade.');
    expect(r.status).toBe(418);
  });
});

describe('marcarDisponibilidade', () => {
  it('manda PUT com o estado e o token, no caminho do produto', async () => {
    chamarFalso.mockResolvedValue({ id: 'x' });

    await marcarDisponibilidade('loja-1', 'prod-1', 'ACABANDO', 't.o.k');

    expect(chamarFalso).toHaveBeenCalledWith(
      '/api/v1/merchants/loja-1/catalog/produtos/prod-1/disponibilidade',
      { token: 't.o.k', metodo: 'PUT', corpo: { estado: 'ACABANDO' } },
    );
  });

  it('a recusa do servidor vira MarcacaoRecusada, com o detail dela', async () => {
    chamarFalso.mockRejectedValue(new ErroDaApi(409, 'Use ESGOTADO_INDETERMINADO.'));

    const erro = await marcarDisponibilidade('l', 'p', 'ESGOTADO_HOJE', 't').catch(
      (causa: unknown) => causa,
    );

    expect(erro).toBeInstanceOf(MarcacaoRecusada);
    expect((erro as MarcacaoRecusada).recusa.detail).toBe('Use ESGOTADO_INDETERMINADO.');
  });

  it('o 401 passa intacto — quem reage é a raiz, porque a sessão já caiu', async () => {
    chamarFalso.mockRejectedValue(new ErroDaApi(401));

    const erro = await marcarDisponibilidade('l', 'p', 'ACABANDO', 't').catch(
      (causa: unknown) => causa,
    );

    expect(erro).toBeInstanceOf(ErroDaApi);
    expect(erro).not.toBeInstanceOf(MarcacaoRecusada);
  });

  it('sem resposta vira recusa de status 0', async () => {
    chamarFalso.mockRejectedValue(new SemResposta(new TypeError('Failed to fetch')));

    const erro = await marcarDisponibilidade('l', 'p', 'ACABANDO', 't').catch(
      (causa: unknown) => causa,
    );

    expect((erro as MarcacaoRecusada).recusa.status).toBe(0);
  });
});

describe('os quatro estados', () => {
  it('são quatro, e cada um tem rótulo', () => {
    expect(ESTADOS).toHaveLength(4);
    for (const estado of ESTADOS) {
      expect(ROTULO[estado]).toBeTruthy();
    }
  });

  it('nenhum rótulo repete, porque dois botões iguais são um defeito de tela', () => {
    const rotulos = ESTADOS.map((e) => ROTULO[e]);
    expect(new Set(rotulos).size).toBe(rotulos.length);
  });

  // premissas-do-front.md §4.1: o desconhecido nunca concede nada.
  it('estado que o front não conhece vira null, e não "disponível"', () => {
    expect(estadoConhecido('ESGOTADO_PARA_SEMPRE')).toBeNull();
    expect(estadoConhecido(undefined)).toBeNull();
    expect(estadoConhecido('ACABANDO')).toBe('ACABANDO');
  });
});
