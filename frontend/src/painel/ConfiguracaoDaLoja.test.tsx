import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';

import { ErroDaApi } from '../api/cliente';
import { guardar } from '../auth/armazenamento';
import { SessaoProvider } from '../auth/SessaoProvider';
import { ConfiguracaoDaLoja } from './ConfiguracaoDaLoja';

vi.mock('../api/cliente', async () => {
  const real = await vi.importActual<typeof import('../api/cliente')>('../api/cliente');
  return { ...real, chamar: vi.fn() };
});

// `chamar` é genérica, e `vi.mocked` de função genérica briga com o typecheck.
const { chamar } = await import('../api/cliente');
const chamarFalso = chamar as unknown as Mock;

const BASE = '/api/v1/merchants/p1/settings';
const HORARIO = { horarioDeFuncionamento: { FRIDAY: [{ inicio: '18:00', fim: '23:00' }] } };
const MINIMO = { pedidoMinimoPorModalidade: { ENTREGA: 20, RETIRADA: 0 } };
const DESCONTO = { descontoDeRetirada: 5 };
const AFOGADOS = {
  nome: 'Afogados',
  taxa: 4.5,
  ativa: true,
  faixasDeCep: [{ inicio: '50750-000', fim: '50770-999' }],
};
const CENTRO = { nome: 'Centro', taxa: 0, ativa: true, faixasDeCep: [] };

type Opcoes = { metodo?: string; corpo?: unknown; token?: string } | undefined;
type Chamada = [string, Opcoes];
type Lido = Record<string, unknown>;

beforeEach(() => {
  guardar({ token: 't.o.k', expiraEm: Date.now() + 1_800_000 });
});

afterEach(() => {
  // A desmontagem antes do reset: um efeito pendente não pode cair no dublê zerado (W-G).
  cleanup();
  chamarFalso.mockReset();
});

/**
 * O GET de cada sub-recurso devolve o que `lido` disser, e o PUT devolve o corpo — ou o que
 * `gravar` decidir para aquele recurso.
 */
function servidor(
  lido: Partial<Record<'horario' | 'pedido-minimo' | 'desconto-retirada' | 'areas-entrega', Lido>>,
  gravar: Record<string, (corpo: unknown) => Promise<unknown>> = {},
) {
  const leitura: Record<string, Lido> = {
    horario: HORARIO,
    'pedido-minimo': MINIMO,
    'desconto-retirada': DESCONTO,
    'areas-entrega': { areasDeEntrega: [AFOGADOS, CENTRO] },
    ...lido,
  };
  chamarFalso.mockImplementation((caminho: string, opcoes?: Opcoes) => {
    const recurso = caminho.slice(BASE.length + 1);
    if (opcoes?.metodo === 'PUT') {
      return (gravar[recurso] ?? ((corpo) => Promise.resolve(corpo)))(opcoes.corpo);
    }
    return Promise.resolve(leitura[recurso]);
  });
}

function montar() {
  return render(
    <SessaoProvider>
      <ConfiguracaoDaLoja estabelecimentoId="p1" />
    </SessaoProvider>,
  );
}

function puts(recurso: string): unknown[] {
  return (chamarFalso.mock.calls as Chamada[])
    .filter(([caminho, opcoes]) => opcoes?.metodo === 'PUT' && caminho === `${BASE}/${recurso}`)
    .map(([, opcoes]) => opcoes?.corpo);
}

function digitar(rotulo: string, valor: string) {
  fireEvent.change(screen.getByLabelText(rotulo), { target: { value: valor } });
}

async function clicar(nome: string) {
  await userEvent.click(screen.getByRole('button', { name: nome }));
}

const minimoGravado = () => within(screen.getByLabelText('Pedido mínimo gravado'));
const areasGravadas = () => within(screen.getByRole('list', { name: 'Áreas gravadas' }));

describe('ConfiguracaoDaLoja · A, a leitura', () => {
  it('1 · lê os quatro sub-recursos e mostra o que está gravado', async () => {
    servidor({});
    montar();

    expect(await screen.findByText('Gravado:')).toBeInTheDocument();
    await screen.findByLabelText('Pedido mínimo gravado');
    await screen.findByRole('list', { name: 'Áreas gravadas' });
    expect(screen.getByLabelText<HTMLInputElement>('Sexta-feira, faixa 1: abre às').value).toBe(
      '18:00',
    );
    expect(minimoGravado().getByText('R$ 20,00')).toBeInTheDocument();
    expect(screen.getByText('R$ 5,00')).toBeInTheDocument();
    expect(areasGravadas().getByText('R$ 4,50')).toBeInTheDocument();
    expect(areasGravadas().getByText(/CEP 50750-000 a 50770-999/)).toBeInTheDocument();

    const lidos = (chamarFalso.mock.calls as Chamada[]).map(([caminho]) => caminho);
    for (const recurso of ['horario', 'pedido-minimo', 'desconto-retirada', 'areas-entrega']) {
      expect(lidos).toContain(`${BASE}/${recurso}`);
    }
  });

  it('2 · o mínimo zero aparece como "sem mínimo", e não como R$ 0,00 (M17)', async () => {
    servidor({});
    montar();

    await screen.findByLabelText('Pedido mínimo gravado');
    const retirada = minimoGravado().getByText('Retirada:').parentElement as HTMLElement;
    expect(within(retirada).getByText('sem mínimo')).toBeInTheDocument();
    // Nenhum zero da tela se lê como preço: o Centro de taxa zero é "entrega grátis".
    expect(screen.queryByText('R$ 0,00')).toBeNull();
    expect(areasGravadas().getByText('entrega grátis')).toBeInTheDocument();
  });

  it('3 · bairro que não está na lista não aparece com taxa zero — ausência não é zero (M11)', async () => {
    servidor({ 'areas-entrega': { areasDeEntrega: [AFOGADOS] } });
    const primeira = montar();

    const lista = await screen.findByRole('list', { name: 'Áreas gravadas' });
    expect(within(lista).getAllByRole('listitem')).toHaveLength(1);
    expect(screen.queryByText('entrega grátis')).toBeNull();
    expect(screen.queryByText('R$ 0,00')).toBeNull();
    primeira.unmount();
    chamarFalso.mockReset();

    // E a loja sem área nenhuma diz que não entrega — nunca "grátis".
    servidor({ 'areas-entrega': { areasDeEntrega: [] } });
    montar();
    expect(await screen.findByText('Esta loja não entrega em nenhum bairro.')).toBeInTheDocument();
    expect(screen.queryByText('entrega grátis')).toBeNull();
    expect(screen.queryByRole('list', { name: 'Áreas gravadas' })).toBeNull();
  });
});

describe('ConfiguracaoDaLoja · B, a escrita', () => {
  it('4 · grava o mínimo das duas modalidades, e mostra o que voltou', async () => {
    servidor({}, {
      'pedido-minimo': () =>
        Promise.resolve({ pedidoMinimoPorModalidade: { ENTREGA: 25.5, RETIRADA: 0 } }),
    });
    montar();
    await screen.findByLabelText('Pedido mínimo na entrega');

    digitar('Pedido mínimo na entrega', '25,50');
    digitar('Pedido mínimo na retirada', '0');
    await clicar('Salvar pedido mínimo');

    expect(await screen.findByText('Pedido mínimo salvo.')).toBeInTheDocument();
    expect(puts('pedido-minimo')).toEqual([
      { pedidoMinimoPorModalidade: { ENTREGA: 25.5, RETIRADA: 0 } },
    ]);
    expect(minimoGravado().getByText('R$ 25,50')).toBeInTheDocument();
    expect(minimoGravado().getByText('sem mínimo')).toBeInTheDocument();
  });

  it('5 · grava o desconto zero — zero é valor, e entra', async () => {
    servidor({});
    montar();
    await screen.findByLabelText('Desconto de retirada');

    digitar('Desconto de retirada', '0');
    await clicar('Salvar desconto');

    expect(await screen.findByText('Desconto de retirada salvo.')).toBeInTheDocument();
    expect(puts('desconto-retirada')).toEqual([{ descontoDeRetirada: 0 }]);
    expect(screen.getByText('sem desconto')).toBeInTheDocument();
  });

  it('6 · acrescenta e tira áreas, com e sem faixa de CEP — o corpo é a lista inteira', async () => {
    servidor({ 'areas-entrega': { areasDeEntrega: [AFOGADOS] } });
    montar();
    await screen.findByLabelText('Área 1: bairro');

    await clicar('Acrescentar área');
    digitar('Área 2: bairro', 'Centro');
    digitar('Área 2: taxa', '0');
    await clicar('Acrescentar área');
    digitar('Área 3: bairro', 'Pina');
    digitar('Área 3: taxa', '9');
    await clicar('Acrescentar faixa de CEP em Área 3');
    digitar('Área 3, faixa 1: CEP inicial', '51010-000');
    digitar('Área 3, faixa 1: CEP final', '51030-999');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Área 3 ativa' }));
    await clicar('Remover Área 1');
    await clicar('Salvar áreas');

    expect(await screen.findByText('Áreas de entrega salvas.')).toBeInTheDocument();
    expect(puts('areas-entrega')).toEqual([
      {
        areasDeEntrega: [
          { nome: 'Centro', taxa: 0, ativa: true, faixasDeCep: [] },
          {
            nome: 'Pina',
            taxa: 9,
            ativa: false,
            faixasDeCep: [{ inicio: '51010-000', fim: '51030-999' }],
          },
        ],
      },
    ]);
    expect(areasGravadas().queryByText('Afogados')).toBeNull();
    expect(areasGravadas().getByText('entrega grátis')).toBeInTheDocument();
    expect(areasGravadas().getByText(/desativada/)).toBeInTheDocument();
  });

  it('7 · a recusa do servidor aparece como veio, e o que foi digitado fica', async () => {
    const frase =
      'duas áreas não podem ter o mesmo nome, e as faixas de CEP de áreas ativas não podem se cruzar';
    servidor({}, { 'areas-entrega': () => Promise.reject(new ErroDaApi(400, frase)) });
    montar();
    await screen.findByLabelText('Área 1: bairro');

    digitar('Área 2: bairro', 'afogados');
    await clicar('Salvar áreas');

    expect((await screen.findByRole('alert')).textContent).toBe(frase);
    expect(screen.getByLabelText<HTMLInputElement>('Área 2: bairro').value).toBe('afogados');
    expect(screen.queryByText('Áreas de entrega salvas.')).toBeNull();
  });

  it('8 · sem a CONFIGURAR_LOJA, o servidor recusa e cada bloco diz isso, sem formulário', async () => {
    // O precedente: o menu não oferece a seção (PainelPage 17), e a tela do horário mostra a
    // frase do 403 sem o texto do servidor (HorarioDaSemana 16, premissas §2.5).
    chamarFalso.mockRejectedValue(new ErroDaApi(403, 'sem acesso a este estabelecimento'));
    montar();

    expect(
      await screen.findByText('Você não tem permissão para ver o pedido mínimo desta loja.'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Você não tem permissão para ver o desconto de retirada desta loja.'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Você não tem permissão para ver as áreas de entrega desta loja.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('sem acesso a este estabelecimento')).toBeNull();
    for (const botao of ['Salvar pedido mínimo', 'Salvar desconto', 'Salvar áreas']) {
      expect(screen.queryByRole('button', { name: botao })).toBeNull();
    }
  });
});
