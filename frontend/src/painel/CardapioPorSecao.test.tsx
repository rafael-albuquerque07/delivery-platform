import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';

import { ErroDaApi } from '../api/cliente';
import { guardar } from '../auth/armazenamento';
import { SessaoProvider } from '../auth/SessaoProvider';
import { CardapioPorSecao } from './CardapioPorSecao';

vi.mock('../api/cliente', async () => {
  const real = await vi.importActual<typeof import('../api/cliente')>('../api/cliente');
  return { ...real, chamar: vi.fn() };
});

// `chamar` é genérica, e `vi.mocked` de função genérica briga com o typecheck.
const { chamar } = await import('../api/cliente');
const chamarFalso = chamar as unknown as Mock;

beforeEach(() => {
  guardar({ token: 't.o.k', expiraEm: Date.now() + 1_800_000 });
});

afterEach(() => {
  chamarFalso.mockReset();
});

const BEBIDAS = { id: 's-beb', nome: 'Bebidas', ordem: 1, ativa: true };
const PIZZAS = { id: 's-piz', nome: 'Pizzas', ordem: 0, ativa: true };

function produto(id: string, nome: string, categoriaId: string, ordem: number, extra = {}) {
  return {
    id,
    nome,
    categoriaId,
    ordem,
    precoBase: 10,
    vendavel: true,
    estadoDePublicacao: 'ATIVO',
    ...extra,
  };
}

/** O dublê das duas rotas que a tela compõe. */
function responder(secoes: unknown[], produtos: unknown[]) {
  chamarFalso.mockImplementation((caminho: string) => {
    if (caminho.endsWith('/catalog/categorias')) return Promise.resolve(secoes);
    if (caminho.includes('/catalog/produtos?')) {
      return Promise.resolve({
        conteudo: produtos,
        pagina: 0,
        tamanho: 100,
        total: produtos.length,
      });
    }
    return Promise.reject(new Error(`caminho inesperado: ${caminho}`));
  });
}

function montar() {
  render(
    <SessaoProvider>
      <CardapioPorSecao estabelecimentoId="p1" />
    </SessaoProvider>,
  );
}

/** Os títulos dos grupos, na ordem em que aparecem. */
function grupos(): string[] {
  return screen.getAllByRole('heading', { level: 3 }).map((titulo) => titulo.textContent ?? '');
}

function nomesEm(grupo: string): string[] {
  const secao = screen.getByRole('region', { name: grupo });
  return within(secao)
    .queryAllByRole('listitem')
    .map((item) => item.textContent ?? '');
}

describe('CardapioPorSecao', () => {
  it('pede as duas rotas: as seções e os produtos, na página do teto', async () => {
    responder([], []);

    montar();

    await screen.findByText(/cardápio está vazio/i);
    const caminhos = (chamarFalso.mock.calls as [string][]).map(([caminho]) => caminho);
    expect(caminhos).toContain('/api/v1/merchants/p1/catalog/categorias');
    expect(caminhos).toContain('/api/v1/merchants/p1/catalog/produtos?page=0&size=100');
  });

  /**
   * Duas seções, e numa delas dois produtos: com uma de cada, um agrupamento que
   * ignorasse o `categoriaId` passaria igual.
   */
  it('A1 · agrupa: seção de ordem menor primeiro, e dentro dela o produto de ordem menor', async () => {
    responder(
      [BEBIDAS, PIZZAS],
      [
        produto('1', 'Guaraná', 's-beb', 0),
        produto('2', 'Calabresa', 's-piz', 1),
        produto('3', 'Margherita', 's-piz', 0),
      ],
    );

    montar();

    await screen.findByText('Margherita');
    expect(grupos()).toEqual(['Pizzas', 'Bebidas']);
    const pizzas = nomesEm('Pizzas');
    expect(pizzas).toHaveLength(2);
    expect(pizzas[0]).toContain('Margherita');
    expect(pizzas[1]).toContain('Calabresa');
    expect(nomesEm('Bebidas')).toHaveLength(1);
    expect(nomesEm('Bebidas')[0]).toContain('Guaraná');
  });

  it('A2 · o produto cuja seção não existe cai em "Sem seção", no fim', async () => {
    responder(
      [PIZZAS],
      [produto('1', 'Margherita', 's-piz', 0), produto('9', 'Órfão', 's-apagada', 0)],
    );

    montar();

    await screen.findByText('Órfão');
    expect(grupos()).toEqual(['Pizzas', 'Sem seção']);
    expect(nomesEm('Sem seção')[0]).toContain('Órfão');
  });

  it('A3 · sem produto órfão, o grupo "Sem seção" não aparece', async () => {
    responder([PIZZAS], [produto('1', 'Margherita', 's-piz', 0)]);

    montar();

    await screen.findByText('Margherita');
    expect(screen.queryByRole('heading', { name: 'Sem seção' })).not.toBeInTheDocument();
  });

  it('A4 · seção vazia aparece, e diz que não tem produto', async () => {
    responder([PIZZAS, BEBIDAS], [produto('1', 'Margherita', 's-piz', 0)]);

    montar();

    await screen.findByText('Margherita');
    const bebidas = screen.getByRole('region', { name: 'Bebidas' });
    expect(within(bebidas).getByText(/nenhum produto nesta seção/i)).toBeInTheDocument();
  });

  /**
   * Um estado que a regra não produziria por si — ATIVO e não vendável é possível, mas a
   * tela não tem como saber disso sem o servidor. Ela mostra o que veio.
   */
  it('A5 · o estado é lido: ATIVO e vendavel false mostra "No cardápio" e "não vendável"', async () => {
    responder([PIZZAS], [produto('1', 'Margherita', 's-piz', 0, { vendavel: false })]);

    montar();

    const linha = (await screen.findByText('Margherita')).closest('li') as HTMLElement;
    expect(within(linha).getByText('No cardápio')).toBeInTheDocument();
    expect(within(linha).getByText('não vendável')).toBeInTheDocument();
  });

  it('o rascunho mostra "Rascunho" e não a marca de não vendável', async () => {
    responder(
      [PIZZAS],
      [produto('1', 'Pizza doce', 's-piz', 0, { vendavel: false, estadoDePublicacao: 'RASCUNHO' })],
    );

    montar();

    const linha = (await screen.findByText('Pizza doce')).closest('li') as HTMLElement;
    expect(within(linha).getByText('Rascunho')).toBeInTheDocument();
    expect(within(linha).queryByText('não vendável')).not.toBeInTheDocument();
  });

  it('estado de publicação desconhecido não ganha rótulo inventado', async () => {
    responder(
      [PIZZAS],
      [produto('1', 'Margherita', 's-piz', 0, { estadoDePublicacao: 'ARQUIVADO' })],
    );

    montar();

    const linha = (await screen.findByText('Margherita')).closest('li') as HTMLElement;
    expect(within(linha).queryByText(/Rascunho|No cardápio|Fora do cardápio/)).toBeNull();
  });

  it('cardápio vazio convida a criar a primeira seção', async () => {
    responder([], []);

    montar();

    expect(await screen.findByText(/Comece criando uma seção/)).toBeInTheDocument();
  });
});

// ── a escrita (commit 2 da W-F) ─────────────────────────────────────────────

type Chamada = [string, { metodo?: string; corpo?: unknown } | undefined];

function chamadas(): Chamada[] {
  return chamarFalso.mock.calls as Chamada[];
}

function postsDeSecao(): unknown[] {
  return chamadas()
    .filter(([caminho, opcoes]) => opcoes?.metodo === 'POST' && caminho.endsWith('/categorias'))
    .map(([, opcoes]) => opcoes?.corpo);
}

/** O dublê com escrita: as seções crescem com o POST, e a publicação responde o que o caso mandar. */
function servidor(
  secoesIniciais: { id: string; nome: string; ordem: number }[],
  produtos: Record<string, unknown>[],
  aoPublicar: (produtoId: string) => Promise<unknown> = (id) =>
    Promise.resolve({ ...produtos.find((p) => p.id === id), estadoDePublicacao: 'ATIVO' }),
) {
  const secoes = [...secoesIniciais];
  chamarFalso.mockImplementation(
    (caminho: string, opcoes?: { metodo?: string; corpo?: unknown }) => {
      if (opcoes?.metodo === 'POST' && caminho.endsWith('/catalog/categorias')) {
        const corpo = opcoes.corpo as { nome: string; ordem: number };
        const nova = { id: `s-${secoes.length + 1}`, ...corpo, ativa: true };
        secoes.push(nova);
        return Promise.resolve(nova);
      }
      if (opcoes?.metodo === 'PUT' && caminho.endsWith('/publicacao')) {
        return aoPublicar(caminho.split('/').at(-2) ?? '');
      }
      if (caminho.endsWith('/catalog/categorias')) return Promise.resolve([...secoes]);
      if (caminho.includes('/catalog/produtos?')) {
        return Promise.resolve({
          conteudo: produtos,
          pagina: 0,
          tamanho: 100,
          total: produtos.length,
        });
      }
      return Promise.reject(new Error(`caminho inesperado: ${caminho}`));
    },
  );
}

function montarComEscrita(aoNovoProduto = vi.fn()) {
  render(
    <SessaoProvider>
      <CardapioPorSecao
        estabelecimentoId="p1"
        podeCriar
        podePublicar
        aoNovoProduto={aoNovoProduto}
      />
    </SessaoProvider>,
  );
  return aoNovoProduto;
}

describe('CardapioPorSecao — a escrita', () => {
  it('B6 · cria a seção com o nome digitado, e a lista se refaz com ela', async () => {
    servidor([PIZZAS], []);

    montarComEscrita();
    await userEvent.type(await screen.findByLabelText('Nova seção'), 'Bebidas');
    await userEvent.click(screen.getByRole('button', { name: 'Criar seção' }));

    expect(await screen.findByRole('heading', { name: 'Bebidas' })).toBeInTheDocument();
    expect(postsDeSecao()).toHaveLength(1);
    expect((postsDeSecao()[0] as { nome: string }).nome).toBe('Bebidas');
  });

  /** Duas seções de ordem diferente: com uma só, um 1 fixo passaria. */
  it('B7 · a ordem enviada é a maior das seções, mais um', async () => {
    servidor([PIZZAS, { ...BEBIDAS, ordem: 4 }], []);

    montarComEscrita();
    await userEvent.type(await screen.findByLabelText('Nova seção'), 'Sobremesas');
    await userEvent.click(screen.getByRole('button', { name: 'Criar seção' }));

    await waitFor(() => expect(postsDeSecao()).toHaveLength(1));
    expect(postsDeSecao()[0]).toEqual({ nome: 'Sobremesas', ordem: 5 });
  });

  it('B8 · nome em branco não chama a rota', async () => {
    servidor([PIZZAS], []);

    montarComEscrita();
    await userEvent.type(await screen.findByLabelText('Nova seção'), '   ');
    await userEvent.click(screen.getByRole('button', { name: 'Criar seção' }));

    expect(await screen.findByText('Dê um nome à seção.')).toBeInTheDocument();
    expect(postsDeSecao()).toHaveLength(0);
  });

  it('o botão de novo produto avisa quem montou a tela', async () => {
    servidor([PIZZAS], []);

    const aoNovoProduto = montarComEscrita();
    await userEvent.click(await screen.findByRole('button', { name: 'Novo produto' }));

    expect(aoNovoProduto).toHaveBeenCalled();
  });

  it('D15 · publicar muda o estado na lista, pelo que o 200 devolveu', async () => {
    servidor(
      [PIZZAS],
      [produto('1', 'Margherita', 's-piz', 0, { estadoDePublicacao: 'RASCUNHO', vendavel: false })],
    );

    montarComEscrita();
    const linha = (await screen.findByText('Margherita')).closest('li') as HTMLElement;
    await userEvent.click(within(linha).getByRole('button', { name: 'Publicar' }));

    expect(await within(linha).findByText('No cardápio')).toBeInTheDocument();
    expect(within(linha).queryByRole('button', { name: 'Publicar' })).toBeNull();
  });

  /** Dois produtos: com um só, uma mensagem no topo da página passaria igual. */
  it('D16 · o 400 aparece ao lado daquele produto, com o detail do servidor, e não do outro', async () => {
    const frase =
      '«Pizza doce» está com preço zero e não vai ao cardápio; ponha o preço e publique de novo';
    servidor(
      [PIZZAS],
      [
        produto('1', 'Pizza doce', 's-piz', 0, { estadoDePublicacao: 'RASCUNHO', vendavel: false }),
        produto('2', 'Calabresa', 's-piz', 1, { estadoDePublicacao: 'RASCUNHO', vendavel: false }),
      ],
      () => Promise.reject(new ErroDaApi(400, frase)),
    );

    montarComEscrita();
    const doce = (await screen.findByText('Pizza doce')).closest('li') as HTMLElement;
    const calabresa = screen.getByText('Calabresa').closest('li') as HTMLElement;
    await userEvent.click(within(doce).getByRole('button', { name: 'Publicar' }));

    expect(await within(doce).findByRole('alert')).toHaveTextContent(frase);
    expect(within(doce).getByRole('alert').textContent).toBe(frase);
    expect(within(calabresa).queryByRole('alert')).toBeNull();
  });

  it('D17 · o produto publicado não tem botão de publicar nem de despublicar', async () => {
    servidor([PIZZAS], [produto('1', 'Margherita', 's-piz', 0)]);

    montarComEscrita();
    const linha = (await screen.findByText('Margherita')).closest('li') as HTMLElement;

    expect(within(linha).queryByRole('button', { name: /publicar/i })).toBeNull();
  });

  it('W-J · com DESATIVAR_PRODUTO, tirar do ar mora ao lado de publicar: DELETE, "Fora do cardápio", e o Publicar volta', async () => {
    servidor([PIZZAS], [produto('1', 'Margherita', 's-piz', 0)]);
    const base = chamarFalso.getMockImplementation() as (c: string, o?: unknown) => unknown;
    chamarFalso.mockImplementation((caminho: string, opcoes?: { metodo?: string }) =>
      opcoes?.metodo === 'DELETE'
        ? Promise.resolve(
            produto('1', 'Margherita', 's-piz', 0, {
              estadoDePublicacao: 'INATIVO',
              vendavel: false,
            }),
          )
        : base(caminho, opcoes),
    );

    render(
      <SessaoProvider>
        <CardapioPorSecao estabelecimentoId="p1" podePublicar podeTirarDoAr />
      </SessaoProvider>,
    );
    const linha = (await screen.findByText('Margherita')).closest('li') as HTMLElement;
    await userEvent.click(within(linha).getByRole('button', { name: 'Tirar do ar' }));

    expect(await within(linha).findByText('Fora do cardápio')).toBeInTheDocument();
    expect(within(linha).getByRole('button', { name: 'Publicar' })).toBeInTheDocument();
    expect(within(linha).queryByRole('button', { name: 'Tirar do ar' })).toBeNull();
    const deletes = chamadas().filter(([, o]) => o?.metodo === 'DELETE');
    expect(deletes.map(([c]) => c)).toEqual(['/api/v1/merchants/p1/catalog/produtos/1/publicacao']);
  });

  it('W-J · o produto fora do ar tem o botão de publicar de novo', async () => {
    servidor(
      [PIZZAS],
      [produto('1', 'Margherita', 's-piz', 0, { estadoDePublicacao: 'INATIVO', vendavel: false })],
    );

    montarComEscrita();
    const linha = (await screen.findByText('Margherita')).closest('li') as HTMLElement;
    await userEvent.click(within(linha).getByRole('button', { name: 'Publicar' }));

    expect(await within(linha).findByText('No cardápio')).toBeInTheDocument();
  });

  it('sem as permissões, nem o campo da seção nem o botão de publicar aparecem', async () => {
    servidor(
      [PIZZAS],
      [produto('1', 'Margherita', 's-piz', 0, { estadoDePublicacao: 'RASCUNHO', vendavel: false })],
    );

    montar();

    await screen.findByText('Margherita');
    expect(screen.queryByLabelText('Nova seção')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Publicar' })).toBeNull();
  });
});
