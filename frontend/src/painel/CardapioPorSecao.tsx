import { useState, type FormEvent } from 'react';

import { criarSecao, despublicarProduto, fraseDaRecusa, publicarProduto } from '../api/cardapio';
import { ErroDaApi } from '../api/cliente';
import { useRecurso } from '../api/useRecurso';
import { useSessao } from '../auth/useSessao';
import { Botao } from '../componentes/Botao';
import { Campo } from '../componentes/Campo';
import {
  agrupar,
  caminhoDasSecoes,
  proximaOrdem,
  secaoParaTela,
  TAMANHO_DO_CARDAPIO,
  type CategoriaDoContrato,
  type GrupoDoCardapio,
  type SecaoDoCardapio,
} from './cardapio';
import {
  caminhoDosProdutos,
  paraTela,
  produtoParaTela,
  rotuloDaPublicacao,
  type PaginaDoContrato,
  type Produto,
} from './produtos';

/**
 * O cardápio da loja, **lido por seção e escrito pela tela** (ADR-067). A tela compõe
 * duas rotas do `catalog` — as seções e os produtos — e agrupa: é a primeira vista deste
 * front montada com duas requisições, e o back não ganha rota de "cardápio agrupado" (§1).
 *
 * ## O que é lido, e não recalculado
 *
 * O estado de publicação vem em cada produto desde a E-3d (ADR-068), e o `vendavel`
 * continua do servidor (W-C). A marca "não vendável" só aparece no que está **no
 * cardápio**: num rascunho, `vendavel: false` é o óbvio — ele nunca foi publicado —, e
 * a marca confundiria "nunca foi publicado" com "acabou tudo". É derivado do que o
 * servidor mandou, campo `estadoDePublicacao`, e não de regra nenhuma.
 *
 * ## A escrita (ADR-067 §3, §5 e §6)
 *
 * - **A seção nasce em linha**, num campo desta tela: dar nome a uma coisa, ao lado da
 *   coisa. A lista se refaz depois do `POST`.
 * - **O produto tem rota própria** — o botão leva a ela.
 * - **Publicar é um botão no rascunho e no que está fora do ar**, e a recusa aparece **ao
 *   lado daquele produto**, com a frase do servidor (ADR-063).
 * - **Tirar do ar é o botão do publicado**, no mesmo lugar (W-J, ADR-079): a rota é o
 *   `DELETE` da E-7, e o produto volta como `INATIVO` — com o botão de publicar de novo.
 *
 * Os botões aparecem por permissão — `CRIAR_PRODUTO` para criar, `ALTERAR_PRODUTO` para
 * publicar, `DESATIVAR_PRODUTO` para tirar do ar —, pelo mesmo critério do menu:
 * conveniência, não segurança (premissas §2.2).
 *
 * ## A página
 *
 * O cardápio pede a página do tamanho do teto da rota. Uma loja com mais produtos do que
 * isso vê o aviso — a paginação da **tela** tem gatilho na ADR-067.
 */
export function CardapioPorSecao({
  estabelecimentoId,
  aoAbrir,
  podeCriar = false,
  podePublicar = false,
  podeTirarDoAr = false,
  aoNovoProduto,
}: {
  estabelecimentoId: string;
  aoAbrir?: (produtoId: string) => void;
  podeCriar?: boolean;
  podePublicar?: boolean;
  podeTirarDoAr?: boolean;
  aoNovoProduto?: () => void;
}) {
  // Recarregar é remontar, como na ListaDeProdutos: o useRecurso de dentro busca de novo.
  const [geracao, definirGeracao] = useState(0);
  return (
    <CardapioCarregado
      key={geracao}
      estabelecimentoId={estabelecimentoId}
      aoAbrir={aoAbrir}
      podeCriar={podeCriar}
      podePublicar={podePublicar}
      podeTirarDoAr={podeTirarDoAr}
      aoNovoProduto={aoNovoProduto}
      aoRecarregar={() => definirGeracao((atual) => atual + 1)}
    />
  );
}

function CardapioCarregado({
  estabelecimentoId,
  aoAbrir,
  podeCriar,
  podePublicar,
  podeTirarDoAr,
  aoNovoProduto,
  aoRecarregar,
}: {
  estabelecimentoId: string;
  aoAbrir: ((produtoId: string) => void) | undefined;
  podeCriar: boolean;
  podePublicar: boolean;
  podeTirarDoAr: boolean;
  aoNovoProduto: (() => void) | undefined;
  aoRecarregar: () => void;
}) {
  const secoes = useRecurso<CategoriaDoContrato[]>(caminhoDasSecoes(estabelecimentoId));
  const produtos = useRecurso<PaginaDoContrato>(
    caminhoDosProdutos(estabelecimentoId, TAMANHO_DO_CARDAPIO),
  );
  // O produto que a publicação devolveu substitui o da lista — é o estado que vale.
  const [devolvidos, definirDevolvidos] = useState<ReadonlyMap<string, Produto>>(new Map());

  if (secoes.estado === 'carregando' || produtos.estado === 'carregando') {
    return <p className="text-sm text-slate-500 dark:text-slate-400">Carregando o cardápio…</p>;
  }

  if (secoes.estado === 'erro' || produtos.estado === 'erro') {
    const erro = secoes.estado === 'erro' ? secoes.erro : (produtos as { erro: Error }).erro;
    const semPermissao = erro instanceof ErroDaApi && erro.status === 403;
    return (
      <p role="alert" className="text-sm text-amber-700 dark:text-amber-400">
        {semPermissao
          ? 'Você não tem permissão para ver o cardápio desta loja.'
          : 'Não deu para carregar o cardápio agora.'}
      </p>
    );
  }

  const listaDeSecoes = (secoes.dados ?? []).map(secaoParaTela);
  const pagina = paraTela(produtos.dados);
  const atuais = pagina.produtos.map((daLista) => devolvidos.get(daLista.id) ?? daLista);
  const grupos = agrupar(listaDeSecoes, atuais);

  return (
    <div className="flex flex-col gap-6">
      {podeCriar && (
        <div className="flex flex-wrap items-end gap-4">
          <NovaSecaoEmLinha
            estabelecimentoId={estabelecimentoId}
            secoes={listaDeSecoes}
            aoCriar={aoRecarregar}
          />
          {aoNovoProduto !== undefined && (
            <Botao type="button" variante="discreto" onClick={aoNovoProduto}>
              Novo produto
            </Botao>
          )}
        </div>
      )}

      {grupos.length === 0 ? (
        <p className="text-sm text-slate-600 dark:text-slate-300">
          Seu cardápio está vazio. Comece criando uma seção, como Bebidas ou Pizzas, e depois
          coloque os produtos nela.
        </p>
      ) : (
        grupos.map((grupo) => (
          <GrupoDaTela
            key={grupo.secao?.id ?? 'sem-secao'}
            grupo={grupo}
            estabelecimentoId={estabelecimentoId}
            aoAbrir={aoAbrir}
            podePublicar={podePublicar}
            podeTirarDoAr={podeTirarDoAr}
            aoPublicado={(produto) =>
              definirDevolvidos((atual) => new Map(atual).set(produto.id, produto))
            }
          />
        ))
      )}

      {pagina.temMais && (
        <p className="text-xs text-slate-500 dark:text-slate-400">
          Mostrando {pagina.produtos.length} de {pagina.total} produtos.
        </p>
      )}
    </div>
  );
}

/**
 * A seção nova, num campo. **O nome em branco não vai ao servidor** — é forma, e é a
 * única validação que esta tela faz. A `ordem` é o maior das seções, mais um: derivada e
 * nunca mostrada (ADR-067 §4).
 */
function NovaSecaoEmLinha({
  estabelecimentoId,
  secoes,
  aoCriar,
}: {
  estabelecimentoId: string;
  secoes: readonly SecaoDoCardapio[];
  aoCriar: () => void;
}) {
  const token = useSessao().sessao?.token;
  const [nome, definirNome] = useState('');
  const [enviando, definirEnviando] = useState(false);
  const [erro, definirErro] = useState<string | undefined>(undefined);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (token === undefined) return;
    if (nome.trim() === '') {
      definirErro('Dê um nome à seção.');
      return;
    }
    definirEnviando(true);
    definirErro(undefined);
    try {
      await criarSecao(estabelecimentoId, { nome, ordem: proximaOrdem(secoes) }, token);
      aoCriar();
    } catch (falha) {
      const frase = fraseDaRecusa(
        falha,
        { 403: 'Você não tem permissão para criar seção nesta loja.' },
        'Não foi possível criar a seção agora.',
      );
      if (frase !== null) definirErro(frase);
      definirEnviando(false);
    }
  }

  return (
    <form
      onSubmit={(evento) => void enviar(evento)}
      onChange={() => definirErro(undefined)}
      className="flex items-end gap-2"
      noValidate
    >
      <Campo
        rotulo="Nova seção"
        value={nome}
        onChange={(e) => definirNome(e.target.value)}
        erro={erro}
        placeholder="Bebidas"
      />
      <Botao type="submit" ocupado={enviando}>
        Criar seção
      </Botao>
    </form>
  );
}

function GrupoDaTela({
  grupo,
  estabelecimentoId,
  aoAbrir,
  podePublicar,
  podeTirarDoAr,
  aoPublicado,
}: {
  grupo: GrupoDoCardapio;
  estabelecimentoId: string;
  aoAbrir: ((produtoId: string) => void) | undefined;
  podePublicar: boolean;
  podeTirarDoAr: boolean;
  aoPublicado: (produto: Produto) => void;
}) {
  const titulo = grupo.secao?.nome ?? 'Sem seção';
  return (
    <section aria-label={titulo}>
      <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
        {titulo}
      </h3>
      {grupo.produtos.length === 0 ? (
        <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
          Nenhum produto nesta seção.
        </p>
      ) : (
        <ul className="divide-y divide-slate-200 dark:divide-slate-700">
          {grupo.produtos.map((produto) => (
            <LinhaDoProduto
              key={produto.id}
              produto={produto}
              estabelecimentoId={estabelecimentoId}
              aoAbrir={aoAbrir}
              podePublicar={podePublicar}
              podeTirarDoAr={podeTirarDoAr}
              aoPublicado={aoPublicado}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function LinhaDoProduto({
  produto,
  estabelecimentoId,
  aoAbrir,
  podePublicar,
  podeTirarDoAr,
  aoPublicado,
}: {
  produto: Produto;
  estabelecimentoId: string;
  aoAbrir: ((produtoId: string) => void) | undefined;
  podePublicar: boolean;
  podeTirarDoAr: boolean;
  aoPublicado: (produto: Produto) => void;
}) {
  const token = useSessao().sessao?.token;
  const [publicando, definirPublicando] = useState(false);
  const [recusa, definirRecusa] = useState<string | null>(null);
  const estado = rotuloDaPublicacao(produto.publicacao);

  // Publicar e tirar do ar são PUT e DELETE no mesmo sub-recurso, `/publicacao`, e o
  // produto que volta substitui o da lista.
  async function alternar(ato: 'publicar' | 'tirar') {
    if (token === undefined) return;
    definirPublicando(true);
    definirRecusa(null);
    try {
      const rota = ato === 'publicar' ? publicarProduto : despublicarProduto;
      aoPublicado(produtoParaTela(await rota(estabelecimentoId, produto.id, token)));
    } catch (falha) {
      definirRecusa(
        fraseDaRecusa(
          falha,
          {
            403:
              ato === 'publicar'
                ? 'Você não tem permissão para publicar nesta loja.'
                : 'Você não tem permissão para tirar produto do ar nesta loja.',
            404: 'Este produto não está mais nesta loja.',
          },
          ato === 'publicar'
            ? 'Não foi possível publicar agora.'
            : 'Não foi possível tirar do ar agora.',
        ),
      );
    } finally {
      definirPublicando(false);
    }
  }

  return (
    <li className="py-3">
      <div className="flex items-baseline justify-between gap-4">
        {aoAbrir === undefined ? (
          <span className="text-slate-900 dark:text-slate-100">{produto.nome}</span>
        ) : (
          <button
            type="button"
            onClick={() => aoAbrir(produto.id)}
            className="text-left text-slate-900 underline-offset-2 hover:underline dark:text-slate-100"
          >
            {produto.nome}
          </button>
        )}
        <span className="flex items-baseline gap-3">
          {estado !== null && (
            <span className="text-xs text-slate-500 dark:text-slate-400">{estado}</span>
          )}
          {produto.publicacao === 'ATIVO' && !produto.vendavel && (
            <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">
              não vendável
            </span>
          )}
          <span className="tabular-nums text-slate-700 dark:text-slate-300">{produto.preco}</span>
          {podePublicar &&
            (produto.publicacao === 'RASCUNHO' || produto.publicacao === 'INATIVO') && (
              <Botao
                type="button"
                variante="discreto"
                ocupado={publicando}
                onClick={() => void alternar('publicar')}
              >
                Publicar
              </Botao>
            )}
          {podeTirarDoAr && produto.publicacao === 'ATIVO' && (
            <Botao
              type="button"
              variante="discreto"
              ocupado={publicando}
              onClick={() => void alternar('tirar')}
            >
              Tirar do ar
            </Botao>
          )}
        </span>
      </div>
      {recusa !== null && (
        <p role="alert" className="mt-1 text-sm text-red-700 dark:text-red-300">
          {recusa}
        </p>
      )}
    </li>
  );
}
