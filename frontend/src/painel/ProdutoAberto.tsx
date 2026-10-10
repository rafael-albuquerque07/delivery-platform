import { useState } from 'react';

import { ErroDaApi } from '../api/cliente';
import {
  marcarDisponibilidadeDaOpcao,
  recusaDe,
  ROTULO,
  type EstadoDeDisponibilidade,
  type ProdutoDoContrato,
} from '../api/disponibilidade';
import { useRecurso } from '../api/useRecurso';
import { Botao } from '../componentes/Botao';
import { MarcarEstado } from './MarcarDisponibilidade';
import {
  caminhoDoProduto,
  produtoAbertoParaTela,
  type GrupoNaTela,
  type ProdutoInteiroDoContrato,
} from './produtoInteiro';

/**
 * O produto aberto: os grupos, as opções, o acréscimo de cada uma e a
 * disponibilidade **por opção** — a tela que fecha o marco 2 (W-D).
 *
 * ## Alcançada pela lista, e não por URL
 *
 * O painel navega por estado, não por rota: as seções são derivadas das
 * permissões e "não há URL que as abra por fora" (W-B, W-C). A tela do produto
 * segue a mesma forma — a lista a abre, e "Voltar à lista" a fecha.
 *
 * ## O vendável é o do servidor
 *
 * Esta tela tem tudo para calculá-lo — os grupos, o mínimo de cada um, o estado de
 * cada opção — e não calcula: mostra o `vendavel` que veio. Ver o
 * `produtoInteiro.ts`.
 *
 * ## Marcar uma opção relê o produto
 *
 * A rota de marcar a opção devolve o **resumo** (G-C2), sem os grupos. Para a tela
 * mostrar o estado novo da opção e o vendável recalculado juntos, ela relê o
 * produto inteiro — remontando, como a lista faz para recarregar. A recusa é a
 * mesma `Recusa` da W-C, pelo mesmo `recusaDe`.
 */
export function ProdutoAberto({
  estabelecimentoId,
  produtoId,
  podeMarcar,
  aoVoltar,
}: {
  estabelecimentoId: string;
  produtoId: string;
  podeMarcar: boolean;
  aoVoltar: () => void;
}) {
  const [geracao, definirGeracao] = useState(0);
  return (
    <ProdutoCarregado
      key={geracao}
      estabelecimentoId={estabelecimentoId}
      produtoId={produtoId}
      podeMarcar={podeMarcar}
      aoVoltar={aoVoltar}
      aoRecarregar={() => definirGeracao((atual) => atual + 1)}
    />
  );
}

function ProdutoCarregado({
  estabelecimentoId,
  produtoId,
  podeMarcar,
  aoVoltar,
  aoRecarregar,
}: {
  estabelecimentoId: string;
  produtoId: string;
  podeMarcar: boolean;
  aoVoltar: () => void;
  aoRecarregar: () => void;
}) {
  const recurso = useRecurso<ProdutoInteiroDoContrato>(
    caminhoDoProduto(estabelecimentoId, produtoId),
  );

  const voltar = (
    <Botao variante="discreto" onClick={aoVoltar}>
      ← Voltar à lista
    </Botao>
  );

  if (recurso.estado === 'carregando') {
    return (
      <section className="flex flex-col gap-3">
        {voltar}
        <p className="text-sm text-slate-500 dark:text-slate-400">Carregando o produto…</p>
      </section>
    );
  }

  if (recurso.estado === 'erro') {
    const status = recurso.erro instanceof ErroDaApi ? recurso.erro.status : 0;
    // A mesma tabela de mensagens da marcação (ADR-055): 404 é "não está mais
    // nesta loja" (ADR-056), 403 é permissão. Nenhuma tradução nova.
    const recusa = recusaDe(status, undefined);
    return (
      <section className="flex flex-col gap-3">
        <p role="alert" className="text-sm text-amber-700 dark:text-amber-400">
          {status === 403
            ? 'Você não tem permissão para ver este produto.'
            : status === 404
              ? recusa.mensagem
              : 'Não deu para carregar o produto agora.'}
        </p>
        {voltar}
      </section>
    );
  }

  const produto = produtoAbertoParaTela(recurso.dados);

  return (
    <section className="flex flex-col gap-4">
      {voltar}

      <header className="flex flex-wrap items-baseline justify-between gap-3">
        <h2 className="text-xl font-semibold text-slate-900 dark:text-slate-50">{produto.nome}</h2>
        <span className="flex items-baseline gap-3">
          {!produto.vendavel && (
            <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">
              não vendável
            </span>
          )}
          <span className="tabular-nums text-slate-700 dark:text-slate-300">{produto.preco}</span>
        </span>
      </header>

      <p className="text-sm text-slate-600 dark:text-slate-300">
        {produto.disponibilidade === null
          ? 'Estado atual não reconhecido por esta tela.'
          : ROTULO[produto.disponibilidade]}
      </p>

      {produto.grupos.length === 0 && (
        <p className="text-sm text-slate-600 dark:text-slate-300">Este produto não tem opções.</p>
      )}

      {produto.grupos.map((grupo) => (
        <Grupo
          key={grupo.id}
          grupo={grupo}
          podeMarcar={podeMarcar}
          enviar={(opcaoId, estado, token) =>
            marcarDisponibilidadeDaOpcao(
              estabelecimentoId,
              produto.id,
              grupo.id,
              opcaoId,
              estado,
              token,
            )
          }
          aoMarcar={aoRecarregar}
          aoRecarregar={aoRecarregar}
        />
      ))}
    </section>
  );
}

function Grupo({
  grupo,
  podeMarcar,
  enviar,
  aoMarcar,
  aoRecarregar,
}: {
  grupo: GrupoNaTela;
  podeMarcar: boolean;
  enviar: (
    opcaoId: string,
    estado: EstadoDeDisponibilidade,
    token: string,
  ) => Promise<ProdutoDoContrato>;
  aoMarcar: () => void;
  aoRecarregar: () => void;
}) {
  return (
    <section
      aria-label={grupo.nome}
      className="rounded border border-slate-200 p-3 dark:border-slate-700"
    >
      <h3 className="font-medium text-slate-900 dark:text-slate-100">
        {grupo.nome}{' '}
        <span className="text-sm font-normal text-slate-500 dark:text-slate-400">
          · {grupo.regra}
        </span>
      </h3>

      {grupo.opcoes.length === 0 ? (
        // Grupo sem opção nenhuma é defeito de cadastro — e obrigatório, ninguém o
        // satisfaz. A tela existe para a pessoa ver isso; lista vazia em silêncio
        // esconderia exatamente o problema.
        <p role="note" className="mt-2 text-sm text-amber-700 dark:text-amber-400">
          {grupo.obrigatorio
            ? 'Nenhuma opção cadastrada — e o grupo é obrigatório, então ninguém consegue escolher.'
            : 'Nenhuma opção cadastrada.'}
        </p>
      ) : (
        <ul className="mt-2 divide-y divide-slate-100 dark:divide-slate-800">
          {grupo.opcoes.map((opcao) => (
            <li key={opcao.id} className="py-2">
              <div className="flex items-baseline justify-between gap-4">
                <span className="text-slate-900 dark:text-slate-100">{opcao.nome}</span>
                <span className="flex items-baseline gap-3 text-sm">
                  {opcao.acrescimo !== null && (
                    <span className="tabular-nums text-slate-700 dark:text-slate-300">
                      {opcao.acrescimo}
                    </span>
                  )}
                  <span className="text-slate-500 dark:text-slate-400">
                    {opcao.disponibilidade === null
                      ? 'estado não reconhecido'
                      : ROTULO[opcao.disponibilidade]}
                  </span>
                </span>
              </div>
              {podeMarcar && (
                <MarcarEstado
                  nome={opcao.nome}
                  atual={opcao.disponibilidade}
                  enviar={(estado, token) => enviar(opcao.id, estado, token)}
                  aoMarcar={aoMarcar}
                  aoRecarregar={aoRecarregar}
                  rotuloDoRecarregar="Recarregar o produto"
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
