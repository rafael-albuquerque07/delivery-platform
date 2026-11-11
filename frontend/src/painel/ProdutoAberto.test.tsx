import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';

import { ErroDaApi } from '../api/cliente';
import { guardar } from '../auth/armazenamento';
import { SessaoProvider } from '../auth/SessaoProvider';
import { ProdutoAberto } from './ProdutoAberto';

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

/**
 * O `ProdutoResponse` do contrato commitado. Grupos e opções vêm **fora** da
 * ordem de propósito: a tela ordena pelo campo `ordem`, não pela lista.
 *
 * E o caso que prova a decisão da W-D está aqui dentro: a opção "Grande" está
 * esgotada e o servidor diz `vendavel: true` — o "Tamanho" ainda tem duas.
 */
function produtoInteiro(campos: Record<string, unknown> = {}) {
  return {
    id: 'p-1',
    nome: 'Pizza margherita',
    precoBase: 49.9,
    estadoDePublicacao: 'ATIVO',
    disponibilidade: 'DISPONIVEL',
    vendavel: true,
    gruposDeOpcoes: [
      {
        id: 'g-adicionais',
        nome: 'Adicionais',
        minEscolhas: 0,
        maxEscolhas: 2,
        ordem: 1,
        opcoes: [
          {
            id: 'o-sem-cebola',
            nome: 'Sem cebola',
            acrescimo: -2,
            ordem: 1,
            disponibilidade: 'DISPONIVEL',
          },
          { id: 'o-bacon', nome: 'Bacon', acrescimo: 6, ordem: 0, disponibilidade: 'DISPONIVEL' },
        ],
      },
      {
        id: 'g-tamanho',
        nome: 'Tamanho',
        minEscolhas: 1,
        maxEscolhas: 1,
        ordem: 0,
        opcoes: [
          {
            id: 'o-grande',
            nome: 'Grande',
            acrescimo: 16,
            ordem: 2,
            disponibilidade: 'ESGOTADO_HOJE',
          },
          {
            id: 'o-pequena',
            nome: 'Pequena',
            acrescimo: 0,
            ordem: 0,
            disponibilidade: 'DISPONIVEL',
          },
          { id: 'o-media', nome: 'Média', acrescimo: 8, ordem: 1, disponibilidade: 'DISPONIVEL' },
        ],
      },
    ],
    ...campos,
  };
}

/** GET devolve o produto inteiro; PUT faz o que o caso mandar. */
function servidor(produto: unknown, aoMarcar: () => Promise<unknown> = () => Promise.resolve({})) {
  chamarFalso.mockImplementation((_caminho: string, opcoes?: { metodo?: string }) =>
    opcoes?.metodo === 'PUT' ? aoMarcar() : Promise.resolve(produto),
  );
}

function leituras(): number {
  const chamadas = chamarFalso.mock.calls as [string, { metodo?: string } | undefined][];
  return chamadas.filter(([, opcoes]) => opcoes?.metodo !== 'PUT').length;
}

function montar({ podeMarcar = false, aoVoltar = () => {} } = {}) {
  render(
    <SessaoProvider>
      <ProdutoAberto
        estabelecimentoId="loja-1"
        produtoId="p-1"
        podeMarcar={podeMarcar}
        aoVoltar={aoVoltar}
      />
    </SessaoProvider>,
  );
}

/** O item da opção, pelo nome dela. */
function itemDa(opcao: string): HTMLElement {
  const item = screen.getByText(opcao).closest('li');
  if (item === null) throw new Error(`a opção ${opcao} não está numa lista`);
  return item;
}

describe('ProdutoAberto', () => {
  it('1 · o produto com grupos aparece inteiro, e na ordem de cada um', async () => {
    servidor(produtoInteiro());

    montar();

    expect(await screen.findByRole('heading', { name: 'Pizza margherita' })).toBeInTheDocument();
    expect(screen.getByText('R$ 49,90')).toBeInTheDocument();
    expect(chamarFalso.mock.calls[0]?.[0]).toBe('/api/v1/merchants/loja-1/catalog/produtos/p-1');

    const grupos = screen.getAllByRole('region').map((grupo) => grupo.getAttribute('aria-label'));
    expect(grupos).toEqual(['Tamanho', 'Adicionais']);

    const tamanho = screen.getByRole('region', { name: 'Tamanho' });
    expect(within(tamanho).getByText(/Escolha 1/)).toBeInTheDocument();
    const nomes = within(tamanho)
      .getAllByRole('listitem')
      .map((item) => item.querySelector('span')?.textContent);
    expect(nomes).toEqual(['Pequena', 'Média', 'Grande']);

    const adicionais = screen.getByRole('region', { name: 'Adicionais' });
    expect(within(adicionais).getByText(/Opcional, até 2/)).toBeInTheDocument();
  });

  it('2 · o acréscimo nas três formas: positivo, negativo, e o zero não aparece', async () => {
    servidor(produtoInteiro());

    montar();
    await screen.findByRole('heading', { name: 'Pizza margherita' });

    expect(within(itemDa('Média')).getByText(/^\+R\$\s8,00$/)).toBeInTheDocument();
    expect(within(itemDa('Sem cebola')).getByText(/^-R\$\s2,00$/)).toBeInTheDocument();
    // O zero: nenhum valor em reais ao lado de "Pequena" — nem "R$ 0,00", nem "+R$ 0,00".
    expect(within(itemDa('Pequena')).queryByText(/R\$/)).toBeNull();
  });

  it('3 · a opção esgotada aparece, e o vendável é o que o servidor disse', async () => {
    servidor(produtoInteiro({ vendavel: true }));

    montar();
    await screen.findByRole('heading', { name: 'Pizza margherita' });

    expect(within(itemDa('Grande')).getByText('Acabou hoje')).toBeInTheDocument();
    expect(screen.queryByText(/não vendável/i)).toBeNull();
  });

  it('3b · e quando o servidor diz que não é vendável, a tela diz também', async () => {
    servidor(produtoInteiro({ vendavel: false }));

    montar();
    await screen.findByRole('heading', { name: 'Pizza margherita' });

    expect(screen.getByText(/não vendável/i)).toBeInTheDocument();
  });

  it('4 · grupo obrigatório sem opção nenhuma renderiza, e diz o que há de errado', async () => {
    servidor(
      produtoInteiro({
        gruposDeOpcoes: [
          { id: 'g-borda', nome: 'Borda', minEscolhas: 1, maxEscolhas: 1, ordem: 0, opcoes: [] },
        ],
      }),
    );

    montar();

    const borda = await screen.findByRole('region', { name: 'Borda' });
    expect(within(borda).getByRole('note')).toHaveTextContent(/nenhuma opção cadastrada/i);
    expect(within(borda).getByRole('note')).toHaveTextContent(/obrigatório/i);
  });

  it('5 · marcar uma opção manda o PUT certo, e relê o produto', async () => {
    servidor(produtoInteiro());

    montar({ podeMarcar: true });
    await screen.findByRole('heading', { name: 'Pizza margherita' });
    const lidasAntes = leituras();

    fireEvent.click(
      within(screen.getByRole('group', { name: 'Disponibilidade de Média' })).getByRole('button', {
        name: 'Acabou hoje',
      }),
    );

    await waitFor(() => expect(leituras()).toBe(lidasAntes + 1));
    const put = (chamarFalso.mock.calls as [string, { metodo?: string }][]).find(
      ([, opcoes]) => opcoes?.metodo === 'PUT',
    );
    expect(put?.[0]).toBe(
      '/api/v1/merchants/loja-1/catalog/produtos/p-1/grupos/g-tamanho/opcoes/o-media/disponibilidade',
    );
    expect(put?.[1]).toEqual({ token: 't.o.k', metodo: 'PUT', corpo: { estado: 'ESGOTADO_HOJE' } });
  });

  describe('6 · a recusa da marcação é a mesma Recusa da W-C', () => {
    async function recusar(erro: ErroDaApi) {
      servidor(produtoInteiro(), () => Promise.reject(erro));
      montar({ podeMarcar: true });
      await screen.findByRole('heading', { name: 'Pizza margherita' });
      fireEvent.click(
        within(screen.getByRole('group', { name: 'Disponibilidade de Média' })).getByRole(
          'button',
          { name: 'Acabou hoje' },
        ),
      );
      return screen.findByRole('alert');
    }

    it('409 mostra o detail do servidor, e oferece recarregar o produto', async () => {
      const alerta = await recusar(new ErroDaApi(409, 'O produto mudou enquanto você marcava.'));

      expect(alerta).toHaveTextContent('O produto mudou enquanto você marcava.');
      expect(
        within(alerta).getByRole('button', { name: 'Recarregar o produto' }),
      ).toBeInTheDocument();
    });

    it('403 não mostra o texto do servidor, e pede o painel', async () => {
      const alerta = await recusar(new ErroDaApi(403, 'texto interno que não vai à tela'));

      expect(alerta).not.toHaveTextContent('texto interno');
      expect(
        within(alerta).getByRole('button', { name: 'Recarregar o painel' }),
      ).toBeInTheDocument();
    });

    it('500 mostra a mensagem fixa', async () => {
      const alerta = await recusar(new ErroDaApi(500, undefined));

      expect(alerta).toHaveTextContent('Algo quebrou do nosso lado. Tente de novo em instantes.');
    });
  });

  it('7 · produto que não existe nesta loja: a mensagem do 404, e volta à lista', async () => {
    chamarFalso.mockRejectedValue(new ErroDaApi(404, undefined));
    const aoVoltar = vi.fn();

    montar({ aoVoltar });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Este produto não está mais nesta loja.',
    );
    fireEvent.click(screen.getByRole('button', { name: /voltar à lista/i }));
    expect(aoVoltar).toHaveBeenCalled();
  });

  it('sem podeMarcar, não há botão de estado nenhum', async () => {
    servidor(produtoInteiro());

    montar({ podeMarcar: false });
    await screen.findByRole('heading', { name: 'Pizza margherita' });

    expect(screen.queryByRole('group')).toBeNull();
  });
});

// ── a montagem dos grupos e das opções (W-J, ADR-079) ───────────────────────

type ChamadaDeMontagem = [string, { metodo?: string; corpo?: unknown } | undefined];

/**
 * O dublê com estado: o GET devolve o produto como ele está, o DELETE da publicação o
 * deixa INATIVO, e os POSTs fazem o que o caso mandar — a tela relê depois de cada um.
 */
function servidorDeMontagem(
  inicial: Record<string, unknown>,
  aoCriar: (caminho: string) => Promise<unknown> = () => Promise.resolve({}),
) {
  let atual = { ...inicial };
  chamarFalso.mockImplementation((caminho: string, opcoes?: { metodo?: string }) => {
    if (opcoes?.metodo === 'DELETE' && caminho.endsWith('/publicacao')) {
      atual = { ...atual, estadoDePublicacao: 'INATIVO' };
      return Promise.resolve(atual);
    }
    if (opcoes?.metodo === 'POST') return aoCriar(caminho);
    return Promise.resolve(atual);
  });
}

function escritas(metodo: string): ChamadaDeMontagem[] {
  return (chamarFalso.mock.calls as ChamadaDeMontagem[]).filter(
    ([, opcoes]) => opcoes?.metodo === metodo,
  );
}

/** Só os GETs — o `leituras()` de cima conta tudo que não é PUT, e o POST entraria. */
function gets(): number {
  return (chamarFalso.mock.calls as ChamadaDeMontagem[]).filter(
    ([, opcoes]) => opcoes?.metodo === undefined,
  ).length;
}

function montarParaMontar({ podeMontar = true, podeTirarDoAr = true } = {}) {
  render(
    <SessaoProvider>
      <ProdutoAberto
        estabelecimentoId="loja-1"
        produtoId="p-1"
        podeMarcar={false}
        podeMontar={podeMontar}
        podeTirarDoAr={podeTirarDoAr}
        aoVoltar={() => {}}
      />
    </SessaoProvider>,
  );
}

describe('ProdutoAberto — montar grupos e opções', () => {
  it('M1 · produto em rascunho: criar grupo manda o POST com a ordem derivada, e relê', async () => {
    servidorDeMontagem(produtoInteiro({ estadoDePublicacao: 'RASCUNHO' }));

    montarParaMontar();
    await screen.findByRole('heading', { name: 'Pizza margherita' });
    const lidasAntes = gets();
    await userEvent.type(screen.getByLabelText('Nome do grupo'), 'Borda');
    await userEvent.type(screen.getByLabelText(/Mínimo de escolhas/), '0');
    await userEvent.type(screen.getByLabelText(/Máximo de escolhas/), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Criar grupo' }));

    await waitFor(() => expect(escritas('POST')).toHaveLength(1));
    const [caminho, opcoes] = escritas('POST')[0] as ChamadaDeMontagem;
    expect(caminho).toBe('/api/v1/merchants/loja-1/catalog/produtos/p-1/grupos');
    // Os grupos do dublê têm ordem 0 e 1: o novo é o 2.
    expect(opcoes?.corpo).toEqual({ nome: 'Borda', minEscolhas: 0, maxEscolhas: 1, ordem: 2 });
    await waitFor(() => expect(gets()).toBe(lidasAntes + 1));
  });

  it('M2 · criar opção num grupo: o POST vai ao grupo certo, com o acréscimo negativo e a ordem derivada', async () => {
    servidorDeMontagem(produtoInteiro({ estadoDePublicacao: 'RASCUNHO' }));

    montarParaMontar();
    const tamanho = await screen.findByRole('region', { name: 'Tamanho' });
    const acrescimo = within(tamanho).getByLabelText(/Acréscimo/);
    await userEvent.type(within(tamanho).getByLabelText('Nome da opção'), 'Brotinho');
    await userEvent.clear(acrescimo);
    await userEvent.type(acrescimo, '-2,50');
    await userEvent.click(within(tamanho).getByRole('button', { name: 'Acrescentar opção' }));

    await waitFor(() => expect(escritas('POST')).toHaveLength(1));
    const [caminho, opcoes] = escritas('POST')[0] as ChamadaDeMontagem;
    expect(caminho).toBe('/api/v1/merchants/loja-1/catalog/produtos/p-1/grupos/g-tamanho/opcoes');
    // As opções do Tamanho têm ordem 0, 1 e 2: a nova é a 3.
    expect(opcoes?.corpo).toEqual({ nome: 'Brotinho', acrescimo: -2.5, ordem: 3 });
  });

  it('M3 · a recusa do servidor aparece como veio, e a tela não reescreve a frase', async () => {
    const frase = 'O grupo «Borda» pede no máximo 0 escolhas, e o máximo é pelo menos 1.';
    servidorDeMontagem(produtoInteiro({ estadoDePublicacao: 'RASCUNHO' }), () =>
      Promise.reject(new ErroDaApi(400, frase)),
    );

    montarParaMontar();
    await screen.findByRole('heading', { name: 'Pizza margherita' });
    await userEvent.type(screen.getByLabelText('Nome do grupo'), 'Borda');
    await userEvent.click(screen.getByRole('button', { name: 'Criar grupo' }));

    expect((await screen.findByRole('alert')).textContent).toBe(frase);
  });

  it('M4 · produto no ar: a frase e a saída no lugar do criar grupo — e a opção continua', async () => {
    servidorDeMontagem(produtoInteiro({ estadoDePublicacao: 'ATIVO' }));

    montarParaMontar();
    await screen.findByRole('heading', { name: 'Pizza margherita' });

    expect(screen.getByText('Para mudar os grupos, tire o produto do ar.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tirar do ar' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Criar grupo' })).toBeNull();
    // O servidor aceita opção num produto publicado (só a C2 a recusaria): o formulário fica.
    const tamanho = screen.getByRole('region', { name: 'Tamanho' });
    expect(within(tamanho).getByRole('button', { name: 'Acrescentar opção' })).toBeInTheDocument();
  });

  it('M5 · tirar do ar manda o DELETE, relê, e a seção volta a aceitar grupo', async () => {
    servidorDeMontagem(produtoInteiro({ estadoDePublicacao: 'ATIVO' }));

    montarParaMontar();
    await userEvent.click(await screen.findByRole('button', { name: 'Tirar do ar' }));

    expect(await screen.findByRole('button', { name: 'Criar grupo' })).toBeInTheDocument();
    expect(screen.queryByText('Para mudar os grupos, tire o produto do ar.')).toBeNull();
    expect(escritas('DELETE').map(([caminho]) => caminho)).toEqual([
      '/api/v1/merchants/loja-1/catalog/produtos/p-1/publicacao',
    ]);
  });

  it('M6 · sem ALTERAR_PRODUTO, nem formulário nem frase — como o cardápio sem as permissões', async () => {
    servidorDeMontagem(produtoInteiro({ estadoDePublicacao: 'ATIVO' }));

    montarParaMontar({ podeMontar: false });
    await screen.findByRole('heading', { name: 'Pizza margherita' });

    expect(screen.queryByRole('button', { name: 'Criar grupo' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Acrescentar opção' })).toBeNull();
    expect(screen.queryByText(/tire o produto do ar/)).toBeNull();
    expect(screen.queryByRole('button', { name: 'Tirar do ar' })).toBeNull();
  });

  it('M6b · sem DESATIVAR_PRODUTO, no ar: a frase fica, o botão não', async () => {
    servidorDeMontagem(produtoInteiro({ estadoDePublicacao: 'ATIVO' }));

    montarParaMontar({ podeTirarDoAr: false });
    await screen.findByRole('heading', { name: 'Pizza margherita' });

    expect(screen.getByText('Para mudar os grupos, tire o produto do ar.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Tirar do ar' })).toBeNull();
  });

  it('estado de publicação desconhecido não abre escrita nenhuma', async () => {
    servidorDeMontagem(produtoInteiro({ estadoDePublicacao: 'ARQUIVADO' }));

    montarParaMontar();
    await screen.findByRole('heading', { name: 'Pizza margherita' });

    expect(screen.queryByRole('button', { name: 'Criar grupo' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Acrescentar opção' })).toBeNull();
    expect(screen.queryByText(/tire o produto do ar/)).toBeNull();
  });
});
