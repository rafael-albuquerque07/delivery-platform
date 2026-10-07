import { useState } from 'react';

import { useRecurso } from '../api/useRecurso';
import { useSessao } from '../auth/useSessao';
import { Botao } from '../componentes/Botao';
import { ListaDeProdutos } from '../painel/ListaDeProdutos';
import { MenuDoPainel } from '../painel/MenuDoPainel';
import { secoesDe, type Secao } from '../painel/secoes';
import { SeletorDeLoja } from '../painel/SeletorDeLoja';
import type { LojaDoUsuario } from '../painel/tipos';

/**
 * O painel: qual loja, o que se pode fazer nela, e o cardápio dela.
 *
 * <h2>O que veio da W-A e continua</h2>
 *
 * O cabeçalho com a sessão e o botão de sair. Não há refresh token (ADR-037:
 * *"o access token é a sessão"*), e esconder a expiração não a evita — só faz a
 * pessoa descobrir no meio de um formulário.
 *
 * <h2>O que a W-B trouxe</h2>
 *
 * As lojas vêm de `GET /api/v1/me/estabelecimentos` (G-B5), com papel e
 * permissões de cada uma. O menu é construído **das permissões**, por loja — e
 * trocar de loja troca o menu. Nada aqui decide acesso: o que protege é o
 * servidor, e a tela só desenha o que ele respondeu.
 *
 * <h2>O que a W-C trouxe</h2>
 *
 * A seção "Disponibilidade", que o `ALTERAR_PRODUTO` abre: a mesma lista, com os
 * quatro estados por produto. A seção é derivada das permissões, como a do
 * cardápio — não há URL que a abra por fora.
 */
export function PainelPage() {
  const { sessao, sair } = useSessao();
  const lojas = useRecurso<LojaDoUsuario[]>('/api/v1/me/estabelecimentos');
  const [escolhida, escolher] = useState<string | null>(null);
  const [secaoEscolhida, escolherSecao] = useState<Secao | null>(null);

  if (sessao === null) return null;

  const minutos = Math.max(0, Math.round((sessao.expiraEm - Date.now()) / 60_000));

  const cabecalho = (
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900 dark:text-slate-50">
          Painel
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Sessão ativa · expira em aproximadamente {minutos} min
        </p>
      </div>
      <Botao variante="discreto" onClick={sair}>
        Sair
      </Botao>
    </header>
  );

  if (lojas.estado === 'carregando') {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-10">
        {cabecalho}
        <p className="text-sm text-slate-500 dark:text-slate-400">Carregando as suas lojas…</p>
      </main>
    );
  }

  if (lojas.estado === 'erro') {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-10">
        {cabecalho}
        <p role="alert" className="text-sm text-amber-700 dark:text-amber-400">
          Não deu para carregar as suas lojas agora.
        </p>
      </main>
    );
  }

  // Derivado, e não guardado num useEffect: a primeira loja é a escolhida até
  // alguém escolher outra. Com efeito haveria um quadro em que nada está
  // escolhido, e a lista de produtos piscaria.
  const selecionada = escolhida ?? lojas.dados[0]?.estabelecimentoId ?? null;
  const loja = lojas.dados.find((uma) => uma.estabelecimentoId === selecionada) ?? null;

  // Mesma derivação, e aqui ela conserta um defeito de verdade: a seção aberta
  // pode não existir na loja para a qual a pessoa acabou de trocar.
  const disponiveis = loja ? secoesDe(loja.permissoes) : [];
  const secao = disponiveis.some((uma) => uma.secao === secaoEscolhida)
    ? secaoEscolhida
    : (disponiveis[0]?.secao ?? null);

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-10">
      {cabecalho}

      <SeletorDeLoja lojas={lojas.dados} selecionada={selecionada} aoSelecionar={escolher} />

      {loja && (
        <>
          <MenuDoPainel permissoes={loja.permissoes} atual={secao} aoEscolher={escolherSecao} />
          {secao === 'cardapio' && <ListaDeProdutos estabelecimentoId={loja.estabelecimentoId} />}
          {secao === 'disponibilidade' && (
            <ListaDeProdutos estabelecimentoId={loja.estabelecimentoId} podeMarcar />
          )}
        </>
      )}
    </main>
  );
}
