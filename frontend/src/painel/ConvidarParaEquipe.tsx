import { useState, type FormEvent } from 'react';

import { fraseDaRecusa } from '../api/cardapio';
import { convidar, linkDoConvite, type PermissaoDoConvite } from '../api/convites';
import { useSessao } from '../auth/useSessao';
import { Aviso } from '../componentes/Aviso';
import { Botao } from '../componentes/Botao';
import { Campo } from '../componentes/Campo';

/** As permissões em português — o enum vem do contrato; o rótulo é daqui. */
const ROTULOS: Record<PermissaoDoConvite, string> = {
  VER_PRODUTO: 'Ver o cardápio',
  CRIAR_PRODUTO: 'Criar seção e produto',
  ALTERAR_PRODUTO: 'Publicar e marcar o que acabou',
  DESATIVAR_PRODUTO: 'Tirar produto do cardápio',
  VER_PEDIDO: 'Ver pedidos',
  ALTERAR_STATUS: 'Mudar o andamento do pedido',
  VER_VENDAS: 'Ver vendas',
  VER_ENTREGA: 'Ver entregas',
  GERENCIAR_EQUIPE: 'Convidar e gerenciar a equipe',
  GERENCIAR_JORNADA: 'Abrir e fechar a jornada dos entregadores',
  LANCAR_PEDIDO: 'Lançar pedido pelo painel',
};

const ORDEM = Object.keys(ROTULOS) as PermissaoDoConvite[];

/**
 * A seção **Equipe**: o convite, e nada mais (ADR-070 §1).
 *
 * <h2>Sem lista, e é de propósito</h2>
 *
 * O `merchant` guarda `usuarioId`, e o nome mora no `identity` — a rota que o traria não
 * existe. Uma lista de membros hoje seria uma lista de identificadores. **Nem lista de
 * convites pendentes**: a rota não existe, e a tela diz isso a quem convida.
 *
 * <h2>O link, uma vez</h2>
 *
 * O 201 devolve o **token**, e o link é montado aqui, com a origem desta aplicação
 * (ADR-070 §2). Ele aparece **em texto** e com um botão de copiar — o botão pode falhar
 * fora de contexto seguro, e o texto é o que garante que o link não se perca. **O token
 * vive só no estado desta tela**: sair dela é perdê-lo, e não há rota que o releia
 * (ADR-069 §1).
 *
 * <h2>As permissões oferecidas</h2>
 *
 * As caixas são as que quem convida **tem** — conveniência, como o menu: quem recusa é o
 * servidor (A2), e a frase dele vai à tela como veio.
 */
export function ConvidarParaEquipe({
  estabelecimentoId,
  minhasPermissoes,
  origem = window.location.origin,
}: {
  estabelecimentoId: string;
  minhasPermissoes: readonly string[];
  origem?: string;
}) {
  const token = useSessao().sessao?.token;
  const [telefone, definirTelefone] = useState('');
  const [marcadas, definirMarcadas] = useState<PermissaoDoConvite[]>([]);
  const [enviando, definirEnviando] = useState(false);
  const [recusa, definirRecusa] = useState<string | null>(null);
  const [link, definirLink] = useState<string | null>(null);
  const [copiou, definirCopiou] = useState<'sim' | 'falhou' | null>(null);

  const oferecidas = ORDEM.filter((permissao) => minhasPermissoes.includes(permissao));

  function alternar(permissao: PermissaoDoConvite) {
    definirMarcadas((atual) =>
      atual.includes(permissao) ? atual.filter((uma) => uma !== permissao) : [...atual, permissao],
    );
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (token === undefined) return;
    if (telefone.trim() === '') {
      definirRecusa('Informe o telefone de quem você vai convidar.');
      return;
    }
    definirEnviando(true);
    definirRecusa(null);
    try {
      const emitido = await convidar(
        estabelecimentoId,
        { telefone: telefone.trim(), permissoes: marcadas },
        token,
      );
      definirLink(linkDoConvite(origem, String(emitido.token ?? '')));
      definirCopiou(null);
    } catch (falha) {
      const frase = fraseDaRecusa(
        falha,
        { 403: 'Você não tem permissão para convidar nesta loja.' },
        'Não foi possível criar o convite agora.',
      );
      if (frase !== null) definirRecusa(frase);
    } finally {
      definirEnviando(false);
    }
  }

  async function copiar(texto: string) {
    try {
      await navigator.clipboard.writeText(texto);
      definirCopiou('sim');
    } catch {
      definirCopiou('falhou');
    }
  }

  return (
    <section className="flex flex-col gap-6">
      <header>
        <h2 className="text-xl font-semibold text-slate-900 dark:text-slate-50">Equipe</h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Convide alguém pelo telefone. Quem aceitar entra na loja como colaborador, com as
          permissões que você marcar.
        </p>
      </header>

      {link !== null && (
        <div className="flex flex-col gap-2 rounded-md border border-slate-300 p-4 dark:border-slate-700">
          <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            Copie agora: este link não aparece de novo.
          </p>
          <p
            data-testid="link-do-convite"
            className="select-all break-all font-mono text-sm text-slate-800 dark:text-slate-200"
          >
            {link}
          </p>
          <div className="flex items-center gap-3">
            <Botao type="button" variante="discreto" onClick={() => void copiar(link)}>
              Copiar link
            </Botao>
            {copiou === 'sim' && <span className="text-xs text-slate-600">Copiado.</span>}
            {copiou === 'falhou' && (
              <span className="text-xs text-slate-600">
                Não deu para copiar daqui. Selecione o link acima e copie.
              </span>
            )}
          </div>
          <p className="text-xs text-slate-600 dark:text-slate-400">
            Não há lista de convites pendentes: depois de sair desta tela, não há onde reencontrar
            este link. Se ele se perder, crie outro convite.
          </p>
        </div>
      )}

      <form
        onSubmit={(evento) => void enviar(evento)}
        onChange={() => definirRecusa(null)}
        className="flex flex-col gap-4"
        noValidate
      >
        <Campo
          rotulo="Telefone de quem você vai convidar"
          inputMode="tel"
          value={telefone}
          onChange={(e) => definirTelefone(e.target.value)}
          aria-required
        />
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-medium text-slate-700 dark:text-slate-200">
            O que essa pessoa vai poder fazer
          </legend>
          {oferecidas.map((permissao) => (
            <label key={permissao} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={marcadas.includes(permissao)}
                onChange={() => alternar(permissao)}
              />
              {ROTULOS[permissao]}
            </label>
          ))}
        </fieldset>

        {recusa !== null && <Aviso tom="erro">{recusa}</Aviso>}

        <div>
          <Botao type="submit" ocupado={enviando}>
            Criar convite
          </Botao>
        </div>
      </form>
    </section>
  );
}
