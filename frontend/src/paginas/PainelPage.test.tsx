import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';

import { App } from '../App';
import { ErroDaApi } from '../api/cliente';
import { guardar } from '../auth/armazenamento';
import { SessaoProvider } from '../auth/SessaoProvider';

vi.mock('../api/cliente', async () => {
  const real = await vi.importActual<typeof import('../api/cliente')>('../api/cliente');
  return { ...real, chamar: vi.fn() };
});

// `chamar` é genérica, e `vi.mocked` de função genérica briga com o typecheck.
// O elenco para `Mock` é o que compila — e o que este arquivo precisa é da
// interface do dublê, não do tipo de retorno da função real.
const { chamar } = await import('../api/cliente');
const chamarFalso = chamar as unknown as Mock;

beforeEach(() => {
  guardar({ token: 't.o.k', expiraEm: Date.now() + 1_800_000 });
});

afterEach(() => {
  chamarFalso.mockReset();
});

/** Onde a navegação terminou. Desde a W-E a loja e a seção moram na URL (ADR-061). */
function OndeEstou() {
  return <p data-testid="onde-estou">{useLocation().pathname}</p>;
}

function montar(endereco = '/painel') {
  return render(
    <MemoryRouter initialEntries={[endereco]}>
      <SessaoProvider>
        <App />
        <OndeEstou />
      </SessaoProvider>
    </MemoryRouter>,
  );
}

function onde() {
  return screen.getByTestId('onde-estou');
}

const PIZZARIA = {
  estabelecimentoId: 'p1',
  nome: 'Pizzaria da Marli',
  papel: 'ADMINISTRADOR',
  permissoes: ['VER_PRODUTO', 'GERENCIAR_EQUIPE'],
};

/** A Bia atende: vê pedido e mexe em status. **Não** vê produto. */
const PADARIA = {
  estabelecimentoId: 'a1',
  nome: 'Ana Pães',
  papel: 'COLABORADOR',
  permissoes: ['VER_PEDIDO', 'ALTERAR_STATUS'],
};

/** Uma segunda loja que vê produto — para os casos em que a URL tem de vencer a primeira. */
const CANTINA = {
  estabelecimentoId: 'c2',
  nome: 'Cantina',
  papel: 'ADMINISTRADOR',
  permissoes: ['VER_PRODUTO'],
};

/** A dona configura a loja: tem `CONFIGURAR_LOJA`, e com ela a seção da configuração, com o horário dentro (ADR-074, ADR-082). */
const QUIOSQUE = {
  estabelecimentoId: 'q3',
  nome: 'Quiosque',
  papel: 'ADMINISTRADOR',
  permissoes: ['VER_PRODUTO', 'CONFIGURAR_LOJA'],
};

const MARGHERITA = { id: 'x9', nome: 'Pizza margherita', precoBase: 49.9, vendavel: true };

function responder(lojas: unknown[], produtos: unknown[] = []) {
  chamarFalso.mockImplementation((caminho: string) => {
    if (caminho === '/api/v1/me/estabelecimentos') return Promise.resolve(lojas);
    if (caminho.endsWith('/settings/horario'))
      return Promise.resolve({ horarioDeFuncionamento: {} });
    // O cardápio compõe as seções com os produtos desde a W-F (ADR-067).
    if (caminho.endsWith('/catalog/categorias')) return Promise.resolve([]);
    if (caminho.includes('?page=')) {
      return Promise.resolve({
        conteudo: produtos,
        pagina: 0,
        tamanho: 20,
        total: produtos.length,
      });
    }
    return Promise.resolve({ ...MARGHERITA, gruposDeOpcoes: [] });
  });
}

function caminhosPedidos(): string[] {
  return (chamarFalso.mock.calls as [string][]).map(([caminho]) => caminho);
}

describe('PainelPage', () => {
  it('B8 · sem vínculo nenhum, mostra o convite com o link do cadastro, e não redireciona', async () => {
    responder([]);

    montar();

    expect(await screen.findByText(/ainda não faz parte de nenhuma loja/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Cadastrar minha loja' })).toHaveAttribute(
      'href',
      '/painel/lojas/nova',
    );
    expect(onde()).toHaveTextContent(/^\/painel$/);
    expect(chamarFalso).toHaveBeenCalledTimes(1);
  });

  /**
   * Antes da W-E: *"com uma loja, escolhe sozinho e já pede o cardápio dela"*, com a
   * loja escolhida em estado. Depois: a mesma coisa visível, e a escolha vira endereço.
   */
  it('B9 · com uma loja, vai direto a ela e já pede o cardápio dela', async () => {
    responder([PIZZARIA]);

    montar();

    await waitFor(() => expect(onde()).toHaveTextContent('/painel/lojas/p1/produtos'));
    await waitFor(() =>
      expect(chamarFalso).toHaveBeenCalledWith(
        '/api/v1/merchants/p1/catalog/produtos?page=0&size=100',
        { token: 't.o.k' },
      ),
    );
    expect(screen.getByText('Pizzaria da Marli')).toBeInTheDocument();
  });

  it('B9 · com duas lojas, mostra o seletor e não pede cardápio nenhum', async () => {
    responder([PADARIA, PIZZARIA]);

    montar();

    expect(await screen.findByRole('combobox', { name: /loja/i })).toHaveValue('');
    expect(onde()).toHaveTextContent(/^\/painel$/);
    expect(caminhosPedidos().some((caminho) => caminho.includes('/catalog/'))).toBe(false);
  });

  it('escolher no seletor leva ao endereço da loja', async () => {
    responder([PADARIA, PIZZARIA]);

    montar();
    await userEvent.selectOptions(await screen.findByRole('combobox', { name: /loja/i }), 'p1');

    await waitFor(() => expect(onde()).toHaveTextContent('/painel/lojas/p1/produtos'));
  });

  it('C10 · /painel/lojas/:id/produtos abre o cardápio daquela loja, e não o da primeira', async () => {
    responder([PIZZARIA, CANTINA]);

    montar('/painel/lojas/c2/produtos');

    await waitFor(() =>
      expect(caminhosPedidos()).toContain('/api/v1/merchants/c2/catalog/produtos?page=0&size=100'),
    );
    expect(caminhosPedidos().some((caminho) => caminho.includes('/merchants/p1/'))).toBe(false);
  });

  it('C11 · /painel/lojas/:id/produtos/:produtoId abre o produto, e voltar volta à lista', async () => {
    responder([PIZZARIA], [MARGHERITA]);

    montar('/painel/lojas/p1/produtos/x9');

    expect(await screen.findByRole('heading', { name: 'Pizza margherita' })).toBeInTheDocument();
    expect(caminhosPedidos()).toContain('/api/v1/merchants/p1/catalog/produtos/x9');

    await userEvent.click(screen.getByRole('button', { name: /voltar à lista/i }));
    await waitFor(() => expect(onde()).toHaveTextContent(/^\/painel\/lojas\/p1\/produtos$/));
  });

  it('/painel/lojas/:id/produtos/novo abre o formulário, e não um produto chamado "novo"', async () => {
    responder([PIZZARIA]);

    montar('/painel/lojas/p1/produtos/novo');

    expect(await screen.findByRole('heading', { name: 'Novo produto' })).toBeInTheDocument();
    expect(caminhosPedidos()).not.toContain('/api/v1/merchants/p1/catalog/produtos/novo');
  });

  /** Duas lojas no contexto, uma com GERENCIAR_EQUIPE e outra sem — e a asserção é sobre as duas. */
  it('A1 · a seção Equipe só aparece na loja em que se tem GERENCIAR_EQUIPE', async () => {
    responder([PIZZARIA, CANTINA]);

    const primeira = montar('/painel/lojas/p1/produtos');
    const menu = await screen.findByRole('navigation', { name: 'Seções desta loja' });
    await userEvent.click(within(menu).getByRole('button', { name: 'Equipe' }));
    await waitFor(() => expect(onde()).toHaveTextContent('/painel/lojas/p1/equipe'));
    expect(await screen.findByRole('heading', { name: 'Equipe' })).toBeInTheDocument();
    primeira.unmount();

    montar('/painel/lojas/c2/produtos');
    const outro = await screen.findByRole('navigation', { name: 'Seções desta loja' });
    expect(within(outro).queryByRole('button', { name: 'Equipe' })).toBeNull();
  });

  it('17 · a seção da configuração só aparece na loja em que se tem CONFIGURAR_LOJA', async () => {
    responder([QUIOSQUE, CANTINA]);

    const primeira = montar('/painel/lojas/q3/produtos');
    const menu = await screen.findByRole('navigation', { name: 'Seções desta loja' });
    expect(within(menu).getByRole('button', { name: 'Configuração da loja' })).toBeInTheDocument();
    primeira.unmount();

    montar('/painel/lojas/c2/produtos');
    const outro = await screen.findByRole('navigation', { name: 'Seções desta loja' });
    expect(within(outro).queryByRole('button', { name: 'Configuração da loja' })).toBeNull();
  });

  it('18 · a seção da configuração vive sob a loja escolhida, e lê o horário daquela loja', async () => {
    responder([QUIOSQUE, CANTINA]);

    montar('/painel/lojas/q3/produtos');
    const menu = await screen.findByRole('navigation', { name: 'Seções desta loja' });
    await userEvent.click(within(menu).getByRole('button', { name: 'Configuração da loja' }));

    await waitFor(() => expect(onde()).toHaveTextContent('/painel/lojas/q3/configuracao'));
    expect(await screen.findByRole('heading', { name: 'Horário' })).toBeInTheDocument();
    expect(caminhosPedidos()).toContain('/api/v1/merchants/q3/settings/horario');
  });

  it('C11 · o nome na lista leva ao endereço do produto', async () => {
    responder([PIZZARIA], [MARGHERITA]);

    montar('/painel/lojas/p1/produtos');
    await userEvent.click(await screen.findByRole('button', { name: 'Pizza margherita' }));

    await waitFor(() => expect(onde()).toHaveTextContent('/painel/lojas/p1/produtos/x9'));
  });

  it('C12 · recarregar mantém a loja: remontar na mesma URL termina na mesma loja', async () => {
    responder([PIZZARIA, CANTINA]);

    const primeira = montar('/painel/lojas/c2/produtos');
    await waitFor(() => expect(onde()).toHaveTextContent('/painel/lojas/c2/produtos'));
    primeira.unmount();
    chamarFalso.mockClear();

    montar('/painel/lojas/c2/produtos');

    await waitFor(() =>
      expect(caminhosPedidos()).toContain('/api/v1/merchants/c2/catalog/produtos?page=0&size=100'),
    );
    expect(screen.getByRole('combobox', { name: /loja/i })).toHaveValue('c2');
  });

  it('C13 · loja de outra pessoa: o 403 vira recusa genérica, sem dizer se a loja existe', async () => {
    chamarFalso.mockImplementation((caminho: string) =>
      caminho === '/api/v1/me/estabelecimentos'
        ? Promise.resolve([PIZZARIA])
        : Promise.reject(new ErroDaApi(403, 'sem acesso a este estabelecimento')),
    );

    montar('/painel/lojas/zz/produtos');

    const recusa = await screen.findByRole('alert');
    expect(recusa).toHaveTextContent(/não tem permissão/i);
    expect(recusa).not.toHaveTextContent(/não existe/i);
    expect(recusa).not.toHaveTextContent('sem acesso a este estabelecimento');
  });

  /**
   * A Bia não tem `VER_PRODUTO` na padaria. Trocar para ela leva ao endereço da loja,
   * e sem seção que as permissões abram, fica o menu dizendo isso — nenhum cardápio
   * pedido que ela não pode ver.
   */
  it('trocar para uma loja sem VER_PRODUTO fecha a seção do cardápio', async () => {
    responder([PADARIA, PIZZARIA]);

    montar('/painel/lojas/p1/produtos');
    await screen.findByRole('button', { name: 'Cardápio' });

    await userEvent.selectOptions(screen.getByRole('combobox', { name: /loja/i }), 'a1');

    await waitFor(() => expect(onde()).toHaveTextContent(/^\/painel\/lojas\/a1$/));
    expect(screen.queryByRole('button', { name: 'Cardápio' })).not.toBeInTheDocument();
    expect(screen.getByText(/ainda não tem nenhuma permissão com tela/i)).toBeInTheDocument();
  });

  it('o menu muda quando a loja muda — é a mesma pessoa com outro vínculo', async () => {
    responder([PADARIA, PIZZARIA]);

    montar('/painel/lojas/a1');

    await screen.findByRole('combobox', { name: /loja/i });
    expect(screen.queryByRole('button', { name: 'Cardápio' })).not.toBeInTheDocument();

    await userEvent.selectOptions(screen.getByRole('combobox', { name: /loja/i }), 'p1');

    expect(await screen.findByRole('button', { name: 'Cardápio' })).toBeInTheDocument();
  });

  it('a sessão e o botão de sair, que vieram da W-A, continuam no cabeçalho', async () => {
    responder([]);

    montar();

    expect(await screen.findByText(/expira em aproximadamente 30 min/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Sair' }));
    expect(sessionStorage.getItem('delivery.sessao')).toBeNull();
  });

  it('erro ao listar as lojas não vira tela em branco', async () => {
    chamarFalso.mockRejectedValue(new Error('rede'));

    montar();

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
