import { useState } from 'react';

import {
  ESTADOS,
  MarcacaoRecusada,
  ROTULO,
  marcarDisponibilidade,
  type EstadoDeDisponibilidade,
  type ProdutoDoContrato,
  type Recusa,
} from '../api/disponibilidade';
import { useSessao } from '../auth/useSessao';
import { Botao } from '../componentes/Botao';
import type { Produto } from './produtos';

/**
 * Os quatro estados de um produto, e um clique para trocar.
 *
 * ## A tela espera
 *
 * Clicar manda o `PUT` e **desabilita os quatro botões até a resposta**. Não há
 * atualização otimista (ADR-055, G-C2): marcar pode derrubar o `vendavel`, e o
 * cliente não consegue derivá-lo. Quem substitui o item é a lista, com o produto
 * que o servidor devolveu — inteiro, não só o estado.
 *
 * ## A recusa
 *
 * Mostra o `detail` do servidor quando houver, senão a mensagem do cliente, e
 * **nunca interpreta o texto** (ADR-055 §2). O 409 da loja sem horário diz "use
 * ESGOTADO_INDETERMINADO" porque é o servidor que escreve a frase.
 *
 * - `ofereceRecarregar` (409, 503, sem resposta): recarregar a lista;
 * - `contextoVelho` (403): a permissão mudou debaixo da tela, e o que está velho
 *   é o painel inteiro — o menu e o seletor de loja. Recarrega a página: a sessão
 *   mora em `sessionStorage` e sobrevive (ADR-047).
 *
 * ## Quem pode
 *
 * Este componente não confere permissão. Ele só é renderizado pela seção
 * "Disponibilidade", e a seção só existe quando o vínculo tem `ALTERAR_PRODUTO`:
 * o `PainelPage` deriva a seção das permissões, e não há rota de URL que a abra
 * por fora. Quem protege é o servidor; a tela só não oferece o que ele recusaria.
 */
export function MarcarDisponibilidade({
  estabelecimentoId,
  produto,
  aoMarcar,
  aoRecarregar,
}: {
  estabelecimentoId: string;
  produto: Produto;
  aoMarcar: (produto: ProdutoDoContrato) => void;
  aoRecarregar: () => void;
}) {
  const token = useSessao().sessao?.token;
  const [enviando, definirEnviando] = useState(false);
  const [recusa, definirRecusa] = useState<Recusa | null>(null);

  async function marcar(estado: EstadoDeDisponibilidade) {
    if (token === undefined) return;
    definirEnviando(true);
    definirRecusa(null);
    try {
      aoMarcar(await marcarDisponibilidade(estabelecimentoId, produto.id, estado, token));
    } catch (erro) {
      if (erro instanceof MarcacaoRecusada) {
        definirRecusa(erro.recusa);
      }
      // O 401 não chega como recusa: o cliente já derrubou a sessão, e a raiz
      // da aplicação troca a tela.
    } finally {
      definirEnviando(false);
    }
  }

  return (
    <div className="mt-2">
      <div
        role="group"
        aria-label={`Disponibilidade de ${produto.nome}`}
        className="flex flex-wrap gap-1"
      >
        {ESTADOS.map((estado) => (
          <button
            key={estado}
            type="button"
            disabled={enviando}
            aria-pressed={produto.disponibilidade === estado}
            onClick={() => void marcar(estado)}
            className={[
              'rounded px-2 py-1 text-xs font-medium',
              'disabled:cursor-not-allowed disabled:opacity-60',
              produto.disponibilidade === estado
                ? 'bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900'
                : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200',
            ].join(' ')}
          >
            {ROTULO[estado]}
          </button>
        ))}
      </div>

      {produto.disponibilidade === null && (
        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
          Estado atual não reconhecido por esta tela.
        </p>
      )}

      {recusa !== null && (
        <div role="alert" className="mt-2 text-sm text-amber-700 dark:text-amber-400">
          <p>{recusa.detail ?? recusa.mensagem}</p>
          {recusa.ofereceRecarregar && (
            <Botao variante="discreto" onClick={aoRecarregar}>
              Recarregar a lista
            </Botao>
          )}
          {recusa.contextoVelho && (
            <Botao variante="discreto" onClick={() => window.location.reload()}>
              Recarregar o painel
            </Botao>
          )}
        </div>
      )}
    </div>
  );
}
