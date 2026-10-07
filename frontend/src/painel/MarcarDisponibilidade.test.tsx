import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';

import { ErroDaApi } from '../api/cliente';
import { guardar } from '../auth/armazenamento';
import { SessaoProvider } from '../auth/SessaoProvider';
import { ListaDeProdutos } from './ListaDeProdutos';
import { secoesDe } from './secoes';

vi.mock('../api/cliente', async () => {
  const real = await vi.importActual<typeof import('../api/cliente')>('../api/cliente');
  return { ...real, chamar: vi.fn() };
});

const { chamar } = await import('../api/cliente');
const chamarFalso = chamar as unknown as Mock;

beforeEach(() => {
  guardar({ token: 't.o.k', expiraEm: Date.now() + 1_800_000 });
});

afterEach(() => {
  chamarFalso.mockReset();
});

/** O `ProdutoResumoResponse` do contrato commitado. */
function produto(campos: Record<string, unknown> = {}) {
  return {
    id: 'p-1',
    nome: 'Pizza calabresa',
    precoBase: 49.9,
    vendavel: true,
    disponibilidade: 'DISPONIVEL',
    ...campos,
  };
}

function pagina(itens: unknown[]) {
  return { conteudo: itens, pagina: 0, tamanho: 20, total: itens.length };
}

/** GET devolve a lista; PUT faz o que o caso mandar. */
function servidor(lista: unknown[], aoMarcar: () => Promise<unknown>) {
  chamarFalso.mockImplementation((_caminho: string, opcoes?: { metodo?: string }) =>
    opcoes?.metodo === 'PUT' ? aoMarcar() : Promise.resolve(pagina(lista)),
  );
}

/** Quantas vezes a lista foi lida — todas as chamadas que não são o PUT. */
function leituras(): number {
  const chamadas = chamarFalso.mock.calls as [string, { metodo?: string } | undefined][];
  return chamadas.filter(([, opcoes]) => opcoes?.metodo !== 'PUT').length;
}

async function montar(podeMarcar = true) {
  render(
    <SessaoProvider>
      <ListaDeProdutos estabelecimentoId="loja-1" podeMarcar={podeMarcar} />
    </SessaoProvider>,
  );
  await screen.findByText('Pizza calabresa');
}

function botao(rotulo: string) {
  return within(
    screen.getByRole('group', { name: /Disponibilidade de Pizza calabresa/ }),
  ).getByRole('button', { name: rotulo });
}

describe('MarcarDisponibilidade', () => {
  it('1 · enquanto espera o servidor, os quatro botões ficam desabilitados', async () => {
    servidor([produto()], () => new Promise(() => {}));
    await montar();

    fireEvent.click(botao('Acabando'));

    await waitFor(() => expect(botao('Disponível')).toBeDisabled());
    expect(botao('Acabou hoje')).toBeDisabled();
  });

  /**
   * O caso que a mutação "otimista" tem de derrubar: a pessoa marcou ACABANDO e
   * o servidor respondeu que o produto deixou de ser vendável. A tela mostra o
   * que o servidor recalculou, e não só o estado que foi clicado.
   *
   * O estado final sozinho não pega a atualização otimista: a resposta chega e
   * sobrescreve o palpite, e o fim fica igual. Por isso a resposta é segurada, e
   * o caso afirma o **meio** — enquanto o servidor não respondeu, nada mudou.
   */
  it('2 · o item é substituído pelo produto da resposta — inteiro, com o vendavel recalculado', async () => {
    let responder: (corpo: unknown) => void = () => {};
    servidor(
      [produto()],
      () =>
        new Promise((resolver) => {
          responder = resolver;
        }),
    );
    await montar();
    expect(screen.queryByText(/não vendável/i)).not.toBeInTheDocument();

    fireEvent.click(botao('Acabando'));

    // O meio: a tela espera, e não adivinha.
    await waitFor(() => expect(botao('Acabando')).toBeDisabled());
    expect(botao('Acabando')).toHaveAttribute('aria-pressed', 'false');
    expect(botao('Disponível')).toHaveAttribute('aria-pressed', 'true');

    responder(produto({ disponibilidade: 'ACABANDO', vendavel: false }));

    expect(await screen.findByText(/não vendável/i)).toBeInTheDocument();
    expect(botao('Acabando')).toHaveAttribute('aria-pressed', 'true');
    expect(botao('Disponível')).toHaveAttribute('aria-pressed', 'false');
  });

  it('3 · a recusa mostra o detail do servidor, como veio', async () => {
    servidor([produto()], () =>
      Promise.reject(
        new ErroDaApi(
          409,
          'esta loja não abre por horário: ESGOTADO_HOJE nunca reativaria. Use ESGOTADO_INDETERMINADO.',
        ),
      ),
    );
    await montar();

    fireEvent.click(botao('Acabou hoje'));

    expect(await screen.findByRole('alert')).toHaveTextContent('Use ESGOTADO_INDETERMINADO.');
  });

  it('3b · sem detail, a recusa mostra a mensagem do cliente para o código', async () => {
    servidor([produto()], () => Promise.reject(new ErroDaApi(503)));
    await montar();

    fireEvent.click(botao('Acabando'));

    expect(await screen.findByRole('alert')).toHaveTextContent(/expediente da loja/);
  });

  it('4 · no 409, oferece recarregar a lista — e recarregar busca de novo', async () => {
    servidor([produto()], () => Promise.reject(new ErroDaApi(409, 'o produto mudou')));
    await montar();
    const leiturasAntes = leituras();

    fireEvent.click(botao('Acabando'));
    fireEvent.click(await screen.findByRole('button', { name: 'Recarregar a lista' }));

    await waitFor(() => expect(leituras()).toBe(leiturasAntes + 1));
  });

  it('5 · no 403, não mostra o texto do servidor e oferece recarregar o painel', async () => {
    servidor([produto()], () =>
      Promise.reject(new ErroDaApi(403, 'sem acesso a este estabelecimento')),
    );
    await montar();

    fireEvent.click(botao('Acabando'));

    const aviso = await screen.findByRole('alert');
    expect(aviso).toHaveTextContent(/não tem mais permissão/);
    expect(aviso).not.toHaveTextContent('sem acesso a este estabelecimento');
    expect(within(aviso).getByRole('button', { name: 'Recarregar o painel' })).toBeInTheDocument();
    expect(within(aviso).queryByRole('button', { name: 'Recarregar a lista' })).toBeNull();
  });

  it('6 · o controle só existe na seção que o ALTERAR_PRODUTO abre', async () => {
    servidor([produto()], () => Promise.resolve(produto()));
    await montar(false);

    expect(screen.queryByRole('group')).not.toBeInTheDocument();
    expect(secoesDe(['ALTERAR_PRODUTO']).map((s) => s.secao)).toEqual(['disponibilidade']);
    expect(secoesDe(['VER_PRODUTO']).map((s) => s.secao)).toEqual(['cardapio']);
  });

  // premissas-do-front.md §4.1: o desconhecido não concede nada.
  it('estado que o front não conhece não aparece como "Disponível"', async () => {
    servidor([produto({ disponibilidade: 'ESGOTADO_PARA_SEMPRE' })], () =>
      Promise.resolve(produto()),
    );
    await montar();

    expect(botao('Disponível')).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByText(/não reconhecido/)).toBeInTheDocument();
  });
});
