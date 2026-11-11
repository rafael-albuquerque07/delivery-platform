import { useState, type FormEvent, type ReactNode } from 'react';

import { criarGrupo, criarOpcao, despublicarProduto, fraseDaRecusa } from '../api/cardapio';
import { ErroDaApi } from '../api/cliente';
import {
  marcarDisponibilidadeDaOpcao,
  recusaDe,
  ROTULO,
  type EstadoDeDisponibilidade,
  type ProdutoDoContrato,
} from '../api/disponibilidade';
import { useRecurso } from '../api/useRecurso';
import { useSessao } from '../auth/useSessao';
import { Botao } from '../componentes/Botao';
import { Campo } from '../componentes/Campo';
import { proximaOrdem } from './cardapio';
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
 *
 * ## Montar os grupos e as opções (W-J, ADR-079)
 *
 * Com `podeMontar`, a tela cria grupo e opção pelas rotas da E-6, e relê o produto depois
 * de cada uma, como na marcação. A recusa é o `detail` do servidor, como veio (ADR-055) —
 * a C3, a C4 e a C2 são do `catalog`, e a tela não as reimplementa.
 *
 * **Uma exceção, escrita na ADR-079:** num produto **no ar**, o lugar do "criar grupo" diz
 * *"Para mudar os grupos, tire o produto do ar."*, com a ação ao lado. O servidor recusa
 * grupo novo num produto publicado **sempre** — o grupo nasce vazio, e a C4 recobrada o
 * recusa (ADR-077) —, e não pelo que se digita. A opção num grupo existente o servidor
 * aceita, e o formulário dela fica.
 */
export function ProdutoAberto({
  estabelecimentoId,
  produtoId,
  podeMarcar,
  podeMontar = false,
  podeTirarDoAr = false,
  aoVoltar,
}: {
  estabelecimentoId: string;
  produtoId: string;
  podeMarcar: boolean;
  podeMontar?: boolean;
  podeTirarDoAr?: boolean;
  aoVoltar: () => void;
}) {
  const [geracao, definirGeracao] = useState(0);
  return (
    <ProdutoCarregado
      key={geracao}
      estabelecimentoId={estabelecimentoId}
      produtoId={produtoId}
      podeMarcar={podeMarcar}
      podeMontar={podeMontar}
      podeTirarDoAr={podeTirarDoAr}
      aoVoltar={aoVoltar}
      aoRecarregar={() => definirGeracao((atual) => atual + 1)}
    />
  );
}

function ProdutoCarregado({
  estabelecimentoId,
  produtoId,
  podeMarcar,
  podeMontar,
  podeTirarDoAr,
  aoVoltar,
  aoRecarregar,
}: {
  estabelecimentoId: string;
  produtoId: string;
  podeMarcar: boolean;
  podeMontar: boolean;
  podeTirarDoAr: boolean;
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
  // Estado de publicação desconhecido não abre escrita nenhuma (premissas §4.1).
  const monta = podeMontar && produto.publicacao !== null;

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
          novaOpcao={
            monta ? (
              <NovaOpcaoNoGrupo
                estabelecimentoId={estabelecimentoId}
                produtoId={produto.id}
                grupo={grupo}
                aoCriar={aoRecarregar}
              />
            ) : null
          }
        />
      ))}

      {monta &&
        (produto.publicacao === 'ATIVO' ? (
          <SaidaDoAr
            estabelecimentoId={estabelecimentoId}
            produtoId={produto.id}
            podeTirarDoAr={podeTirarDoAr}
            aoTirar={aoRecarregar}
          />
        ) : (
          <NovoGrupoNoProduto
            estabelecimentoId={estabelecimentoId}
            produtoId={produto.id}
            grupos={produto.grupos}
            aoCriar={aoRecarregar}
          />
        ))}
    </section>
  );
}

/** Texto do formulário para o número do contrato — leitura, não conta. Vazio vira `NaN`, que o JSON escreve `null`. */
function numeroDe(texto: string): number {
  return texto.trim() === '' ? Number.NaN : Number(texto.replace(',', '.'));
}

/**
 * O produto no ar: o que é verdade, e a saída (ADR-079). Sem `DESATIVAR_PRODUTO`, a
 * frase fica e o botão não — ela continua verdadeira.
 */
function SaidaDoAr({
  estabelecimentoId,
  produtoId,
  podeTirarDoAr,
  aoTirar,
}: {
  estabelecimentoId: string;
  produtoId: string;
  podeTirarDoAr: boolean;
  aoTirar: () => void;
}) {
  const token = useSessao().sessao?.token;
  const [enviando, definirEnviando] = useState(false);
  const [recusa, definirRecusa] = useState<string | null>(null);

  async function tirar() {
    if (token === undefined) return;
    definirEnviando(true);
    definirRecusa(null);
    try {
      await despublicarProduto(estabelecimentoId, produtoId, token);
      aoTirar();
    } catch (falha) {
      definirRecusa(
        fraseDaRecusa(
          falha,
          {
            403: 'Você não tem permissão para tirar produto do ar nesta loja.',
            404: 'Este produto não está mais nesta loja.',
          },
          'Não foi possível tirar do ar agora.',
        ),
      );
      definirEnviando(false);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-3">
        <p className="text-sm text-slate-700 dark:text-slate-200">
          Para mudar os grupos, tire o produto do ar.
        </p>
        {podeTirarDoAr && (
          <Botao type="button" variante="discreto" ocupado={enviando} onClick={() => void tirar()}>
            Tirar do ar
          </Botao>
        )}
      </div>
      {recusa !== null && (
        <p role="alert" className="text-sm text-red-700 dark:text-red-300">
          {recusa}
        </p>
      )}
    </div>
  );
}

/**
 * O grupo novo. A `ordem` é o maior dos grupos, mais um — derivada e nunca mostrada, como
 * a da seção e a do produto (ADR-067 §4).
 */
function NovoGrupoNoProduto({
  estabelecimentoId,
  produtoId,
  grupos,
  aoCriar,
}: {
  estabelecimentoId: string;
  produtoId: string;
  grupos: readonly GrupoNaTela[];
  aoCriar: () => void;
}) {
  const token = useSessao().sessao?.token;
  const [nome, definirNome] = useState('');
  const [minimo, definirMinimo] = useState('');
  const [maximo, definirMaximo] = useState('');
  const [enviando, definirEnviando] = useState(false);
  const [recusa, definirRecusa] = useState<string | null>(null);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (token === undefined) return;
    definirEnviando(true);
    definirRecusa(null);
    try {
      await criarGrupo(
        estabelecimentoId,
        produtoId,
        {
          nome,
          minEscolhas: numeroDe(minimo),
          maxEscolhas: numeroDe(maximo),
          ordem: proximaOrdem(grupos),
        },
        token,
      );
      aoCriar();
    } catch (falha) {
      const frase = fraseDaRecusa(
        falha,
        {
          400: 'Confira os campos: nome, mínimo e máximo.',
          403: 'Você não tem permissão para mudar este produto.',
          404: 'Este produto não está mais nesta loja.',
        },
        'Não foi possível criar o grupo agora.',
      );
      if (frase !== null) definirRecusa(frase);
      definirEnviando(false);
    }
  }

  return (
    <form
      onSubmit={(evento) => void enviar(evento)}
      onChange={() => definirRecusa(null)}
      className="flex flex-col gap-3 rounded border border-dashed border-slate-300 p-3 dark:border-slate-700"
      noValidate
    >
      <p className="font-medium text-slate-900 dark:text-slate-100">Novo grupo de opções</p>
      <Campo
        rotulo="Nome do grupo"
        value={nome}
        onChange={(e) => definirNome(e.target.value)}
        placeholder="Tamanho"
      />
      <div className="flex flex-wrap gap-3">
        <Campo
          rotulo="Mínimo de escolhas"
          inputMode="numeric"
          value={minimo}
          onChange={(e) => definirMinimo(e.target.value)}
          dica="Zero se for opcional."
        />
        <Campo
          rotulo="Máximo de escolhas"
          inputMode="numeric"
          value={maximo}
          onChange={(e) => definirMaximo(e.target.value)}
        />
      </div>
      {recusa !== null && (
        <p role="alert" className="text-sm text-red-700 dark:text-red-300">
          {recusa}
        </p>
      )}
      <div>
        <Botao type="submit" ocupado={enviando}>
          Criar grupo
        </Botao>
      </div>
    </form>
  );
}

/** A opção nova, dentro do grupo. A `ordem` é a maior do grupo, mais um. */
function NovaOpcaoNoGrupo({
  estabelecimentoId,
  produtoId,
  grupo,
  aoCriar,
}: {
  estabelecimentoId: string;
  produtoId: string;
  grupo: GrupoNaTela;
  aoCriar: () => void;
}) {
  const token = useSessao().sessao?.token;
  const [nome, definirNome] = useState('');
  const [acrescimo, definirAcrescimo] = useState('0');
  const [enviando, definirEnviando] = useState(false);
  const [recusa, definirRecusa] = useState<string | null>(null);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (token === undefined) return;
    definirEnviando(true);
    definirRecusa(null);
    try {
      await criarOpcao(
        estabelecimentoId,
        produtoId,
        grupo.id,
        { nome, acrescimo: numeroDe(acrescimo), ordem: proximaOrdem(grupo.opcoes) },
        token,
      );
      aoCriar();
    } catch (falha) {
      const frase = fraseDaRecusa(
        falha,
        {
          400: 'Confira os campos: nome e acréscimo.',
          403: 'Você não tem permissão para mudar este produto.',
          404: 'Este grupo não está mais neste produto.',
        },
        'Não foi possível acrescentar a opção agora.',
      );
      if (frase !== null) definirRecusa(frase);
      definirEnviando(false);
    }
  }

  return (
    <form
      onSubmit={(evento) => void enviar(evento)}
      onChange={() => definirRecusa(null)}
      className="mt-3 flex flex-col gap-2"
      noValidate
    >
      <div className="flex flex-wrap items-end gap-3">
        <Campo
          rotulo="Nome da opção"
          value={nome}
          onChange={(e) => definirNome(e.target.value)}
          placeholder="Grande"
        />
        <Campo
          rotulo="Acréscimo"
          inputMode="decimal"
          value={acrescimo}
          onChange={(e) => definirAcrescimo(e.target.value)}
          dica="Em reais. Zero e negativo também valem, como -2,00."
        />
        <Botao type="submit" variante="discreto" ocupado={enviando}>
          Acrescentar opção
        </Botao>
      </div>
      {recusa !== null && (
        <p role="alert" className="text-sm text-red-700 dark:text-red-300">
          {recusa}
        </p>
      )}
    </form>
  );
}

function Grupo({
  grupo,
  podeMarcar,
  enviar,
  aoMarcar,
  aoRecarregar,
  novaOpcao,
}: {
  /** O formulário da opção nova, quando a tela monta — ou nada. */
  novaOpcao: ReactNode;
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
      {novaOpcao}
    </section>
  );
}
