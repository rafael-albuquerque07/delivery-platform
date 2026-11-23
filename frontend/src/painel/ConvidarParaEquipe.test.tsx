import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';

import { ErroDaApi } from '../api/cliente';
import { guardar } from '../auth/armazenamento';
import { SessaoProvider } from '../auth/SessaoProvider';
import { ConvidarParaEquipe } from './ConvidarParaEquipe';

vi.mock('../api/cliente', async () => {
  const real = await vi.importActual<typeof import('../api/cliente')>('../api/cliente');
  return { ...real, chamar: vi.fn() };
});

// `chamar` é genérica, e `vi.mocked` de função genérica briga com o typecheck.
const { chamar } = await import('../api/cliente');
const chamarFalso = chamar as unknown as Mock;

const ORIGEM = 'https://painel.exemplo';
const TOKEN = 'Abc_123-def';
const MINHAS = ['VER_PEDIDO', 'ALTERAR_STATUS', 'GERENCIAR_EQUIPE'];

beforeEach(() => {
  guardar({ token: 't.o.k', expiraEm: Date.now() + 1_800_000 });
});

afterEach(() => {
  chamarFalso.mockReset();
});

function montar() {
  return render(
    <SessaoProvider>
      <ConvidarParaEquipe estabelecimentoId="p1" minhasPermissoes={MINHAS} origem={ORIGEM} />
    </SessaoProvider>,
  );
}

type Chamada = [string, { metodo?: string; corpo?: unknown; token?: string } | undefined];

async function preencherEEnviar() {
  await userEvent.type(
    screen.getByLabelText('Telefone de quem você vai convidar'),
    '(81) 98765-4321',
  );
  await userEvent.click(screen.getByRole('checkbox', { name: 'Ver pedidos' }));
  await userEvent.click(screen.getByRole('button', { name: 'Criar convite' }));
}

describe('ConvidarParaEquipe', () => {
  it('oferece só as permissões que quem convida tem', () => {
    montar();

    expect(
      screen.getAllByRole('checkbox').map((caixa) => caixa.parentElement?.textContent),
    ).toEqual(['Ver pedidos', 'Mudar o andamento do pedido', 'Convidar e gerenciar a equipe']);
  });

  it('M3-3 · quem tem LANCAR_PEDIDO vê a caixa "Lançar pedido pelo painel"', () => {
    render(
      <SessaoProvider>
        <ConvidarParaEquipe
          estabelecimentoId="p1"
          minhasPermissoes={[...MINHAS, 'LANCAR_PEDIDO']}
          origem={ORIGEM}
        />
      </SessaoProvider>,
    );

    expect(screen.getByRole('checkbox', { name: 'Lançar pedido pelo painel' })).toBeInTheDocument();
  });

  it('A2 · o formulário manda o corpo do contrato, e nada além', async () => {
    chamarFalso.mockResolvedValue({ token: TOKEN, expiraEm: '2026-10-22T12:00:00Z' });

    montar();
    await preencherEEnviar();

    await waitFor(() => expect(chamarFalso).toHaveBeenCalled());
    const [caminho, opcoes] = chamarFalso.mock.calls[0] as Chamada;
    expect(caminho).toBe('/api/v1/merchants/p1/team/convites');
    expect(opcoes).toEqual({
      metodo: 'POST',
      corpo: { telefone: '(81) 98765-4321', permissoes: ['VER_PEDIDO'] },
      token: 't.o.k',
    });
  });

  /** A string inteira: "contém o token" não separa uma origem errada de uma certa. */
  it('A3/A4 · o 201 mostra o link, em texto, com a origem da aplicação e o token', async () => {
    chamarFalso.mockResolvedValue({ token: TOKEN, expiraEm: '2026-10-22T12:00:00Z' });

    montar();
    await preencherEEnviar();

    expect(await screen.findByTestId('link-do-convite')).toHaveTextContent(
      /^https:\/\/painel\.exemplo\/convite\/Abc_123-def$/,
    );
    expect(screen.getByText('Copie agora: este link não aparece de novo.')).toBeInTheDocument();
    expect(screen.getByText(/Não há lista de convites pendentes/)).toBeInTheDocument();
  });

  it('A5 · o link some ao sair e voltar, e nenhuma requisição o busca', async () => {
    chamarFalso.mockResolvedValue({ token: TOKEN, expiraEm: '2026-10-22T12:00:00Z' });

    const primeira = montar();
    await preencherEEnviar();
    await screen.findByTestId('link-do-convite');
    primeira.unmount();
    chamarFalso.mockClear();

    montar();

    expect(screen.queryByTestId('link-do-convite')).toBeNull();
    expect(screen.queryByText(new RegExp(TOKEN))).toBeNull();
    expect(chamarFalso).not.toHaveBeenCalled();
    expect(JSON.stringify(sessionStorage)).not.toContain(TOKEN);
  });

  it('A6 · a recusa da A2 na emissão vai à tela como veio', async () => {
    const frase = 'você só pode oferecer permissões que você mesmo tem';
    chamarFalso.mockRejectedValue(new ErroDaApi(400, frase));

    montar();
    await preencherEEnviar();

    expect((await screen.findByRole('alert')).textContent).toBe(frase);
  });

  it('a recusa sai quando um campo muda', async () => {
    chamarFalso.mockRejectedValue(new ErroDaApi(400, 'telefone inválido'));

    montar();
    await preencherEEnviar();
    expect(await screen.findByText('telefone inválido')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Telefone de quem você vai convidar'), '0');

    expect(screen.queryByText('telefone inválido')).toBeNull();
  });

  it('o botão de copiar que falha não esconde o link', async () => {
    chamarFalso.mockResolvedValue({ token: TOKEN, expiraEm: '2026-10-22T12:00:00Z' });
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: () => Promise.reject(new Error('contexto inseguro')) },
    });

    montar();
    await preencherEEnviar();
    await userEvent.click(await screen.findByRole('button', { name: 'Copiar link' }));

    expect(await screen.findByText(/Selecione o link acima e copie/)).toBeInTheDocument();
    expect(screen.getByTestId('link-do-convite')).toHaveTextContent(TOKEN);
  });
});
