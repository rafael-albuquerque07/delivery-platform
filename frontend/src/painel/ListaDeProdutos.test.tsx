import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';

import { ErroDaApi } from '../api/cliente';
import { guardar } from '../auth/armazenamento';
import { SessaoProvider } from '../auth/SessaoProvider';
import { ListaDeProdutos } from './ListaDeProdutos';

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

/** A forma do `PaginaResponseProdutoResumoResponse` do contrato commitado. */
function pagina(itens: unknown[], total = itens.length) {
  return { conteudo: itens, pagina: 0, tamanho: 20, total };
}

function montar(estabelecimentoId: string | null) {
  render(
    <SessaoProvider>
      <ListaDeProdutos estabelecimentoId={estabelecimentoId} />
    </SessaoProvider>,
  );
}

describe('ListaDeProdutos', () => {
  it('sem loja escolhida, não chama a API', () => {
    montar(null);

    expect(chamarFalso).not.toHaveBeenCalled();
  });

  it('o caminho carrega a loja escolhida e a paginação', async () => {
    chamarFalso.mockResolvedValue(pagina([]));

    montar('p1');

    await waitFor(() => expect(chamarFalso).toHaveBeenCalled());
    expect(chamarFalso.mock.calls[0]?.[0]).toBe(
      '/api/v1/merchants/p1/catalog/produtos?page=0&size=20',
    );
    expect(chamarFalso.mock.calls[0]?.[1]).toEqual({ token: 't.o.k' });
  });

  /**
   * A coluna que esta tela existe para mostrar. A rota devolve os publicados,
   * não os vendáveis — e um produto ativo e não vendável é o que o comerciante
   * precisa ver para agir.
   */
  it('produto não vendável aparece, e aparece marcado', async () => {
    chamarFalso.mockResolvedValue(
      pagina([{ id: '1', nome: 'Pizza calabresa', precoBase: 49.9, vendavel: false }]),
    );

    montar('p1');

    expect(await screen.findByText('Pizza calabresa')).toBeInTheDocument();
    expect(screen.getByText(/não vendável/i)).toBeInTheDocument();
  });

  it('produto vendável não ganha marca nenhuma', async () => {
    chamarFalso.mockResolvedValue(
      pagina([{ id: '1', nome: 'Refrigerante', precoBase: 8, vendavel: true }]),
    );

    montar('p1');

    expect(await screen.findByText('Refrigerante')).toBeInTheDocument();
    expect(screen.queryByText(/não vendável/i)).not.toBeInTheDocument();
  });

  it('o preço sai com duas casas e vírgula, mesmo quando o JSON o encurta', async () => {
    chamarFalso.mockResolvedValue(
      pagina([{ id: '1', nome: 'Pizza', precoBase: 49.9, vendavel: true }]),
    );

    montar('p1');

    // 49.90 no servidor chega como 49.9 depois do JSON.parse; trocar o ponto
    // pela vírgula daria "R$ 49,9".
    expect(await screen.findByText('R$ 49,90')).toBeInTheDocument();
  });

  it('403 diz que é permissão, e não "erro"', async () => {
    chamarFalso.mockRejectedValue(new ErroDaApi(403));

    montar('p1');

    expect(await screen.findByRole('alert')).toHaveTextContent(/não tem permissão/i);
  });

  it('500 não fala em permissão', async () => {
    chamarFalso.mockRejectedValue(new ErroDaApi(500));

    montar('p1');

    const aviso = await screen.findByRole('alert');
    expect(aviso).toHaveTextContent(/não deu para carregar/i);
    expect(aviso).not.toHaveTextContent(/permissão/i);
  });

  it('cardápio vazio é uma frase, e não uma lista de zero linhas', async () => {
    chamarFalso.mockResolvedValue(pagina([]));

    montar('p1');

    expect(await screen.findByText(/ainda não tem nenhum produto publicado/i)).toBeInTheDocument();
  });

  it('com mais páginas, diz quantos está mostrando', async () => {
    chamarFalso.mockResolvedValue(
      pagina([{ id: '1', nome: 'Pizza', precoBase: 49.9, vendavel: true }], 37),
    );

    montar('p1');

    expect(await screen.findByText(/Mostrando 1 de 37/)).toBeInTheDocument();
  });

  // W-D: o nome abre o produto inteiro, e voltar relê a lista — marcar uma opção
  // pode ter mudado o vendável de quem está nela.
  it('o nome abre o produto, e voltar relê a lista', async () => {
    chamarFalso.mockImplementation((caminho: string) =>
      Promise.resolve(
        caminho.includes('?page=')
          ? pagina([{ id: 'x9', nome: 'Pizza margherita', precoBase: 49.9, vendavel: true }])
          : {
              id: 'x9',
              nome: 'Pizza margherita',
              precoBase: 49.9,
              vendavel: true,
              gruposDeOpcoes: [],
            },
      ),
    );

    montar('p1');
    fireEvent.click(await screen.findByRole('button', { name: 'Pizza margherita' }));

    expect(await screen.findByRole('heading', { name: 'Pizza margherita' })).toBeInTheDocument();
    expect(chamarFalso.mock.calls.at(-1)?.[0]).toBe('/api/v1/merchants/p1/catalog/produtos/x9');

    fireEvent.click(screen.getByRole('button', { name: /voltar à lista/i }));

    expect(await screen.findByRole('button', { name: 'Pizza margherita' })).toBeInTheDocument();
    expect(chamarFalso.mock.calls.filter(([c]) => String(c).includes('?page='))).toHaveLength(2);
  });
});
