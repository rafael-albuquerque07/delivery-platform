import { useState, type FormEvent, type ReactNode } from 'react';

import { fraseDaRecusa } from '../api/cardapio';
import {
  caminhoDasAreas,
  caminhoDoDesconto,
  caminhoDoPedidoMinimo,
  definirAreas,
  definirDesconto,
  definirPedidoMinimo,
  type AreasDeEntrega,
  type DescontoDeRetirada,
  type PedidoMinimo,
} from '../api/configuracao';
import { useRecurso, type Recurso } from '../api/useRecurso';
import { useSessao } from '../auth/useSessao';
import { Aviso } from '../componentes/Aviso';
import { Botao } from '../componentes/Botao';
import { Campo } from '../componentes/Campo';
import { HorarioDaSemana } from './HorarioDaSemana';
import { precoLegivel } from './produtos';

/**
 * A seção **Configuração da loja** (ADR-082): o que a `CONFIGURAR_LOJA` cobre, numa tela — o
 * horário, o pedido mínimo, o desconto de retirada e as áreas de entrega. Cada bloco lê e
 * salva o seu sub-recurso, como as rotas são (ADR-073 §1): um `PUT` por bloco, e a tela mostra
 * o que voltou.
 *
 * <h2>Zero é um valor, e ausência não é zero</h2>
 *
 * As duas regras que a E-9 e a E-10 prenderam no servidor, ditas do lado de quem lê. O mínimo
 * zero é **"sem mínimo"** (M17), o desconto zero é **"sem desconto"**, e a área de taxa zero é
 * **entrega grátis** — cada um com a sua frase, e nenhum como "R$ 0,00", que se lê como um
 * preço. E o bairro que não está na lista **não aparece**: a lista mostra só as áreas que
 * existem, e a loja sem área diz que não entrega em bairro nenhum (M11). *Mostrar a ausência
 * como taxa zero diria "entrego de graça" onde o sistema diz "não entrego".*
 *
 * <h2>A tela não valida o que o domínio decide</h2>
 *
 * M15, M17, M9 e M10 são do servidor; a recusa volta com o `detail` dele e vai à tela como
 * veio (ADR-055). A tela lê o texto do campo como número e não faz conta nenhuma
 * (`premissas-do-front.md` §3).
 */
export function ConfiguracaoDaLoja({ estabelecimentoId }: { estabelecimentoId: string }) {
  return (
    <div className="flex flex-col gap-10">
      <header>
        <h2 className="text-xl font-semibold text-slate-900 dark:text-slate-50">
          Configuração da loja
        </h2>
      </header>
      <HorarioDaSemana estabelecimentoId={estabelecimentoId} />
      <BlocoDoPedidoMinimo estabelecimentoId={estabelecimentoId} />
      <BlocoDoDesconto estabelecimentoId={estabelecimentoId} />
      <BlocoDasAreas estabelecimentoId={estabelecimentoId} />
    </div>
  );
}

// ------------------------------------------------------------------ as frases do zero

/** O mínimo zero não é um preço: é a ausência de mínimo (M17). */
function minimoLegivel(valor: number | undefined): string {
  return valor === 0 ? 'sem mínimo' : precoLegivel(valor);
}

function descontoLegivel(valor: number | undefined): string {
  return valor === 0 ? 'sem desconto' : precoLegivel(valor);
}

/** Taxa zero é entrega grátis **nesta área** — e só existe porque a área existe (M11). */
function taxaLegivel(valor: number | undefined): string {
  return valor === 0 ? 'entrega grátis' : precoLegivel(valor);
}

/** Texto do formulário para o número do contrato — leitura, não conta. Vazio vira `NaN`. */
function numeroDe(texto: string): number {
  return texto.trim() === '' ? Number.NaN : Number(texto.replace(',', '.'));
}

function textoDe(valor: number | undefined): string {
  return valor === undefined ? '' : String(valor).replace('.', ',');
}

/**
 * As modalidades não estão no contrato (o mapa é de chave `string`, medido na W-E). A tela
 * mostra as chaves que a leitura devolveu, e nenhuma outra; a desconhecida aparece com o nome
 * que veio, sem conceder nada (`premissas-do-front.md` §4.1).
 */
const ROTULO_DA_MODALIDADE: Record<string, string> = { ENTREGA: 'Entrega', RETIRADA: 'Retirada' };
const ORDEM_DA_MODALIDADE = ['ENTREGA', 'RETIRADA'];

function rotuloDaModalidade(chave: string): string {
  return ROTULO_DA_MODALIDADE[chave] ?? chave;
}

function emOrdem(chaves: string[]): string[] {
  const conhecidas = ORDEM_DA_MODALIDADE.filter((chave) => chaves.includes(chave));
  const outras = chaves.filter((chave) => !ORDEM_DA_MODALIDADE.includes(chave)).sort();
  return [...conhecidas, ...outras];
}

// ------------------------------------------------------------------ o bloco

function Bloco<T>({
  titulo,
  descricao,
  recurso,
  carregando,
  semPermissao,
  falha,
  children,
}: {
  titulo: string;
  descricao: string;
  recurso: Recurso<T>;
  carregando: string;
  semPermissao: string;
  falha: string;
  children: (dados: T) => ReactNode;
}) {
  const frase =
    recurso.estado === 'erro' ? fraseDaRecusa(recurso.erro, { 403: semPermissao }, falha) : null;
  return (
    <section className="flex flex-col gap-4">
      <header>
        <h3 className="text-lg font-semibold text-slate-900 dark:text-slate-50">{titulo}</h3>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">{descricao}</p>
      </header>
      {recurso.estado === 'carregando' && (
        <p className="text-sm text-slate-600 dark:text-slate-400">{carregando}</p>
      )}
      {frase !== null && <Aviso tom="erro">{frase}</Aviso>}
      {recurso.estado === 'pronto' && children(recurso.dados)}
    </section>
  );
}

/** O estado de uma gravação: enviando, a recusa que voltou, e o "salvo". */
function useGravacao(fraseDo403: string, falha: string) {
  const token = useSessao().sessao?.token;
  const [enviando, definirEnviando] = useState(false);
  const [recusa, definirRecusa] = useState<string | null>(null);
  const [salvo, definirSalvo] = useState(false);

  async function gravar<R>(enviar: (token: string) => Promise<R>, aoGravar: (r: R) => void) {
    if (token === undefined) return;
    definirEnviando(true);
    definirRecusa(null);
    definirSalvo(false);
    try {
      aoGravar(await enviar(token));
      definirSalvo(true);
    } catch (erro) {
      const frase = fraseDaRecusa(erro, { 403: fraseDo403 }, falha);
      if (frase !== null) definirRecusa(frase);
    } finally {
      definirEnviando(false);
    }
  }

  return { enviando, recusa, salvo, gravar, mexeu: () => definirSalvo(false) };
}

// ------------------------------------------------------------------ o pedido mínimo

function BlocoDoPedidoMinimo({ estabelecimentoId }: { estabelecimentoId: string }) {
  const recurso = useRecurso<PedidoMinimo>(caminhoDoPedidoMinimo(estabelecimentoId));
  return (
    <Bloco
      titulo="Pedido mínimo"
      descricao="Por modalidade, sobre o valor dos itens, sem a taxa de entrega. Zero é sem mínimo."
      recurso={recurso}
      carregando="Carregando o pedido mínimo…"
      semPermissao="Você não tem permissão para ver o pedido mínimo desta loja."
      falha="Não foi possível ler o pedido mínimo agora."
    >
      {(dados) => (
        <EditorDoPedidoMinimo
          estabelecimentoId={estabelecimentoId}
          inicial={dados.pedidoMinimoPorModalidade ?? {}}
        />
      )}
    </Bloco>
  );
}

function EditorDoPedidoMinimo({
  estabelecimentoId,
  inicial,
}: {
  estabelecimentoId: string;
  inicial: Record<string, number>;
}) {
  const [gravado, definirGravado] = useState(inicial);
  const [textos, definirTextos] = useState<Record<string, string>>(() =>
    Object.fromEntries(Object.entries(inicial).map(([chave, valor]) => [chave, textoDe(valor)])),
  );
  const gravacao = useGravacao(
    'Você não tem permissão para mudar o pedido mínimo desta loja.',
    'Não foi possível salvar o pedido mínimo agora.',
  );
  const chaves = emOrdem(Object.keys(gravado));

  if (chaves.length === 0) {
    return (
      <p className="text-sm text-slate-700 dark:text-slate-300">
        Nenhuma modalidade tem pedido mínimo gravado.
      </p>
    );
  }

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    const pedidoMinimoPorModalidade = Object.fromEntries(
      chaves.map((chave) => [chave, numeroDe(textos[chave] ?? '')]),
    );
    void gravacao.gravar(
      (token) => definirPedidoMinimo(estabelecimentoId, { pedidoMinimoPorModalidade }, token),
      (voltou) => {
        const novo = voltou.pedidoMinimoPorModalidade ?? {};
        definirGravado(novo);
        definirTextos(
          Object.fromEntries(Object.entries(novo).map(([chave, valor]) => [chave, textoDe(valor)])),
        );
      },
    );
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <dl aria-label="Pedido mínimo gravado" className="flex flex-col gap-1 text-sm">
        {chaves.map((chave) => (
          <div key={chave} className="flex gap-2">
            <dt className="font-medium text-slate-800 dark:text-slate-200">
              {rotuloDaModalidade(chave)}:
            </dt>
            <dd className="tabular-nums text-slate-700 dark:text-slate-300">
              {minimoLegivel(gravado[chave])}
            </dd>
          </div>
        ))}
      </dl>
      {chaves.map((chave) => (
        <Campo
          key={chave}
          rotulo={`Pedido mínimo na ${rotuloDaModalidade(chave).toLowerCase()}`}
          inputMode="decimal"
          value={textos[chave] ?? ''}
          onChange={(evento) => {
            definirTextos((atual) => ({ ...atual, [chave]: evento.target.value }));
            gravacao.mexeu();
          }}
        />
      ))}
      {gravacao.recusa !== null && <Aviso tom="erro">{gravacao.recusa}</Aviso>}
      {gravacao.salvo && <Aviso tom="neutro">Pedido mínimo salvo.</Aviso>}
      <div>
        <Botao type="submit" ocupado={gravacao.enviando}>
          Salvar pedido mínimo
        </Botao>
      </div>
    </form>
  );
}

// ------------------------------------------------------------------ o desconto de retirada

function BlocoDoDesconto({ estabelecimentoId }: { estabelecimentoId: string }) {
  const recurso = useRecurso<DescontoDeRetirada>(caminhoDoDesconto(estabelecimentoId));
  return (
    <Bloco
      titulo="Desconto de retirada"
      descricao="Em reais, abatido do pedido de quem retira no balcão. Zero é sem desconto."
      recurso={recurso}
      carregando="Carregando o desconto de retirada…"
      semPermissao="Você não tem permissão para ver o desconto de retirada desta loja."
      falha="Não foi possível ler o desconto de retirada agora."
    >
      {(dados) => (
        <EditorDoDesconto estabelecimentoId={estabelecimentoId} inicial={dados.descontoDeRetirada} />
      )}
    </Bloco>
  );
}

function EditorDoDesconto({
  estabelecimentoId,
  inicial,
}: {
  estabelecimentoId: string;
  inicial: number | undefined;
}) {
  const [gravado, definirGravado] = useState(inicial);
  const [texto, definirTexto] = useState(textoDe(inicial));
  const gravacao = useGravacao(
    'Você não tem permissão para mudar o desconto de retirada desta loja.',
    'Não foi possível salvar o desconto de retirada agora.',
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    void gravacao.gravar(
      (token) =>
        definirDesconto(estabelecimentoId, { descontoDeRetirada: numeroDe(texto) }, token),
      (voltou) => {
        definirGravado(voltou.descontoDeRetirada);
        definirTexto(textoDe(voltou.descontoDeRetirada));
      },
    );
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <p className="text-sm text-slate-700 dark:text-slate-300">
        Gravado: <span className="tabular-nums">{descontoLegivel(gravado)}</span>
      </p>
      <Campo
        rotulo="Desconto de retirada"
        inputMode="decimal"
        value={texto}
        onChange={(evento) => {
          definirTexto(evento.target.value);
          gravacao.mexeu();
        }}
      />
      {gravacao.recusa !== null && <Aviso tom="erro">{gravacao.recusa}</Aviso>}
      {gravacao.salvo && <Aviso tom="neutro">Desconto de retirada salvo.</Aviso>}
      <div>
        <Botao type="submit" ocupado={gravacao.enviando}>
          Salvar desconto
        </Botao>
      </div>
    </form>
  );
}

// ------------------------------------------------------------------ as áreas de entrega

type AreaGravada = NonNullable<AreasDeEntrega['areasDeEntrega']>[number];
type FaixaEditada = { inicio: string; fim: string };
type AreaEditada = { nome: string; taxa: string; ativa: boolean; faixas: FaixaEditada[] };

function editadaDe(area: AreaGravada): AreaEditada {
  return {
    nome: area.nome ?? '',
    taxa: textoDe(area.taxa),
    ativa: area.ativa ?? false,
    faixas: (area.faixasDeCep ?? []).map((faixa) => ({
      inicio: faixa.inicio ?? '',
      fim: faixa.fim ?? '',
    })),
  };
}

function BlocoDasAreas({ estabelecimentoId }: { estabelecimentoId: string }) {
  const recurso = useRecurso<AreasDeEntrega>(caminhoDasAreas(estabelecimentoId));
  return (
    <Bloco
      titulo="Áreas de entrega"
      descricao="Os bairros em que a loja entrega, cada um com a sua taxa. Bairro fora da lista não é atendido — não é entrega grátis."
      recurso={recurso}
      carregando="Carregando as áreas de entrega…"
      semPermissao="Você não tem permissão para ver as áreas de entrega desta loja."
      falha="Não foi possível ler as áreas de entrega agora."
    >
      {(dados) => (
        <EditorDasAreas estabelecimentoId={estabelecimentoId} inicial={dados.areasDeEntrega ?? []} />
      )}
    </Bloco>
  );
}

function EditorDasAreas({
  estabelecimentoId,
  inicial,
}: {
  estabelecimentoId: string;
  inicial: AreaGravada[];
}) {
  const [gravadas, definirGravadas] = useState(inicial);
  const [areas, definirAreasEditadas] = useState<AreaEditada[]>(() => inicial.map(editadaDe));
  const gravacao = useGravacao(
    'Você não tem permissão para mudar as áreas de entrega desta loja.',
    'Não foi possível salvar as áreas de entrega agora.',
  );

  function mudar(indice: number, area: AreaEditada) {
    definirAreasEditadas((atual) => atual.map((uma, i) => (i === indice ? area : uma)));
    gravacao.mexeu();
  }

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    const areasDeEntrega = areas.map((area) => ({
      nome: area.nome,
      taxa: numeroDe(area.taxa),
      ativa: area.ativa,
      faixasDeCep: area.faixas,
    }));
    void gravacao.gravar(
      (token) => definirAreas(estabelecimentoId, { areasDeEntrega }, token),
      (voltou) => {
        const novas = voltou.areasDeEntrega ?? [];
        definirGravadas(novas);
        definirAreasEditadas(novas.map(editadaDe));
      },
    );
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-6" noValidate>
      {gravadas.length === 0 ? (
        <p className="text-sm font-medium text-slate-800 dark:text-slate-200">
          Esta loja não entrega em nenhum bairro.
        </p>
      ) : (
        <ul aria-label="Áreas gravadas" className="flex flex-col gap-1 text-sm">
          {gravadas.map((area) => (
            <li key={area.nome} className="text-slate-700 dark:text-slate-300">
              <span className="font-medium text-slate-800 dark:text-slate-200">{area.nome}</span>
              {': '}
              <span className="tabular-nums">{taxaLegivel(area.taxa)}</span>
              {area.ativa === false && ' (desativada: não cota)'}
              {(area.faixasDeCep ?? []).map((faixa) => (
                <span key={`${faixa.inicio}-${faixa.fim}`}>
                  {` · CEP ${faixa.inicio ?? ''} a ${faixa.fim ?? ''}`}
                </span>
              ))}
            </li>
          ))}
        </ul>
      )}

      {areas.map((area, indice) => {
        const nome = `Área ${indice + 1}`;
        return (
          <fieldset key={indice} className="flex flex-col gap-3">
            <legend className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              {nome}
            </legend>
            <Campo
              rotulo={`${nome}: bairro`}
              value={area.nome}
              onChange={(evento) => mudar(indice, { ...area, nome: evento.target.value })}
            />
            <Campo
              rotulo={`${nome}: taxa`}
              inputMode="decimal"
              value={area.taxa}
              dica="Zero é entrega grátis nesta área."
              onChange={(evento) => mudar(indice, { ...area, taxa: evento.target.value })}
            />
            <label className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200">
              <input
                type="checkbox"
                checked={area.ativa}
                onChange={(evento) => mudar(indice, { ...area, ativa: evento.target.checked })}
              />
              {`${nome} ativa`}
            </label>
            {area.faixas.map((faixa, j) => (
              <div key={j} className="flex flex-wrap items-end gap-3">
                <Campo
                  rotulo={`${nome}, faixa ${j + 1}: CEP inicial`}
                  value={faixa.inicio}
                  onChange={(evento) =>
                    mudar(indice, {
                      ...area,
                      faixas: area.faixas.map((uma, k) =>
                        k === j ? { ...uma, inicio: evento.target.value } : uma,
                      ),
                    })
                  }
                />
                <Campo
                  rotulo={`${nome}, faixa ${j + 1}: CEP final`}
                  value={faixa.fim}
                  onChange={(evento) =>
                    mudar(indice, {
                      ...area,
                      faixas: area.faixas.map((uma, k) =>
                        k === j ? { ...uma, fim: evento.target.value } : uma,
                      ),
                    })
                  }
                />
                <Botao
                  type="button"
                  variante="discreto"
                  onClick={() =>
                    mudar(indice, { ...area, faixas: area.faixas.filter((_, k) => k !== j) })
                  }
                >
                  {`Remover a faixa ${j + 1} de ${nome}`}
                </Botao>
              </div>
            ))}
            <div className="flex flex-wrap gap-3">
              <Botao
                type="button"
                variante="discreto"
                onClick={() =>
                  mudar(indice, { ...area, faixas: [...area.faixas, { inicio: '', fim: '' }] })
                }
              >
                {`Acrescentar faixa de CEP em ${nome}`}
              </Botao>
              <Botao
                type="button"
                variante="discreto"
                onClick={() => {
                  definirAreasEditadas((atual) => atual.filter((_, i) => i !== indice));
                  gravacao.mexeu();
                }}
              >
                {`Remover ${nome}`}
              </Botao>
            </div>
          </fieldset>
        );
      })}

      <div>
        <Botao
          type="button"
          variante="discreto"
          onClick={() => {
            definirAreasEditadas((atual) => [
              ...atual,
              { nome: '', taxa: '', ativa: true, faixas: [] },
            ]);
            gravacao.mexeu();
          }}
        >
          Acrescentar área
        </Botao>
      </div>
      {gravacao.recusa !== null && <Aviso tom="erro">{gravacao.recusa}</Aviso>}
      {gravacao.salvo && <Aviso tom="neutro">Áreas de entrega salvas.</Aviso>}
      <div>
        <Botao type="submit" ocupado={gravacao.enviando}>
          Salvar áreas
        </Botao>
      </div>
    </form>
  );
}
