import { Navigate, Outlet, useLocation, useNavigate, useParams } from 'react-router';

import { CardapioPorSecao } from './CardapioPorSecao';
import { ConvidarParaEquipe } from './ConvidarParaEquipe';
import { HorarioDaSemana } from './HorarioDaSemana';
import { ProdutoAberto } from './ProdutoAberto';
import { ListaDeProdutos } from './ListaDeProdutos';
import { caminhoDaLoja, caminhoDaSecao, caminhoDoNovoProduto, secaoDoCaminho } from './caminhos';
import { useContextoDoPainel } from './contextoDoPainel';
import { MenuDoPainel } from './MenuDoPainel';
import { NovoProduto } from './NovoProduto';
import { secoesDe, type Secao } from './secoes';
import { SeletorDeLoja } from './SeletorDeLoja';

/**
 * Uma loja do painel — `/painel/lojas/:estabelecimentoId` (ADR-061).
 *
 * <h2>O id vem da URL, e o front não confere vínculo</h2>
 *
 * O menu é desenhado das permissões que o `GET /api/v1/me/estabelecimentos` devolveu
 * para esta loja. Uma loja que não está na lista não tem permissão nenhuma no menu — e
 * a tela embaixo pede os dados assim mesmo: quem decide é o servidor, que responde
 * 403 idêntico a "não existe" (M7). Conferir vínculo aqui seria derivar a invariante 9
 * no navegador.
 */
export function LojaDoPainel() {
  const { estabelecimentoId = '' } = useParams();
  const contexto = useContextoDoPainel();
  const { lojas } = contexto;
  const navegar = useNavigate();
  const local = useLocation();

  const loja = lojas.find((uma) => uma.estabelecimentoId === estabelecimentoId);
  const permissoes = loja?.permissoes ?? [];

  return (
    <>
      <SeletorDeLoja
        lojas={lojas}
        selecionada={estabelecimentoId}
        aoSelecionar={(outra) => void navegar(caminhoDaLoja(outra))}
      />
      <MenuDoPainel
        permissoes={permissoes}
        atual={secaoDoCaminho(local.pathname)}
        aoEscolher={(secao) => void navegar(caminhoDaSecao(estabelecimentoId, secao))}
      />
      {/* O contexto de um Outlet vale só para o nível de baixo: repassado aqui, ele chega
          às seções — sem isto a SecaoInicial recebia undefined (medido na W-E). */}
      <Outlet context={contexto} />
    </>
  );
}

/**
 * `/painel/lojas/:id` sem seção: a primeira que as permissões abrem, como era quando a
 * seção morava em estado. Sem nenhuma, fica só o menu dizendo isso.
 */
export function SecaoInicial() {
  const { estabelecimentoId = '' } = useParams();
  const { lojas } = useContextoDoPainel();
  const loja = lojas.find((uma) => uma.estabelecimentoId === estabelecimentoId);
  const primeira = secoesDe(loja?.permissoes ?? [])[0]?.secao;

  return primeira === undefined ? null : (
    <Navigate to={caminhoDaSecao(estabelecimentoId, primeira)} replace />
  );
}

/** As duas seções que existem são a mesma lista; "Disponibilidade" a mostra com a marcação. */
const MARCA: Record<Secao, boolean> = {
  cardapio: false,
  disponibilidade: true,
  equipe: false,
  horario: false,
};

/**
 * O cardápio é lido por seção desde a W-F (ADR-067); a disponibilidade continua a lista
 * plana com a marcação — ela é sobre o que acabou, e não sobre onde o item mora.
 */
export function ListaDaSecao({ secao }: { secao: Secao }) {
  const { estabelecimentoId = '' } = useParams();
  const navegar = useNavigate();
  const abrir = (produtoId: string) =>
    void navegar(`${caminhoDaSecao(estabelecimentoId, secao)}/${produtoId}`);
  if (secao === 'cardapio') {
    return <CardapioDaLoja estabelecimentoId={estabelecimentoId} aoAbrir={abrir} />;
  }
  return (
    <ListaDeProdutos
      estabelecimentoId={estabelecimentoId}
      podeMarcar={MARCA[secao]}
      aoAbrir={abrir}
    />
  );
}

/**
 * O cardápio com os botões que as permissões desta loja abrem — conveniência, como o
 * menu (premissas §2.2): quem recusa é o servidor.
 */
function CardapioDaLoja({
  estabelecimentoId,
  aoAbrir,
}: {
  estabelecimentoId: string;
  aoAbrir: (produtoId: string) => void;
}) {
  const { lojas } = useContextoDoPainel();
  const navegar = useNavigate();
  const permissoes =
    lojas.find((uma) => uma.estabelecimentoId === estabelecimentoId)?.permissoes ?? [];
  return (
    <CardapioPorSecao
      estabelecimentoId={estabelecimentoId}
      aoAbrir={aoAbrir}
      podeCriar={permissoes.includes('CRIAR_PRODUTO')}
      podePublicar={permissoes.includes('ALTERAR_PRODUTO')}
      podeTirarDoAr={permissoes.includes('DESATIVAR_PRODUTO')}
      aoNovoProduto={() => void navegar(caminhoDoNovoProduto(estabelecimentoId))}
    />
  );
}

/** `/painel/lojas/:id/produtos/novo` — no 201, volta ao cardápio, que relê. */
export function NovoProdutoDaLoja() {
  const { estabelecimentoId = '' } = useParams();
  const navegar = useNavigate();
  const voltar = () => void navegar(caminhoDaSecao(estabelecimentoId, 'cardapio'));
  return <NovoProduto estabelecimentoId={estabelecimentoId} aoCriar={voltar} aoVoltar={voltar} />;
}

/**
 * `/painel/lojas/:id/equipe` — o convite, e sem lista (ADR-070 §1). As caixas oferecidas
 * são as permissões que o `GET /me/estabelecimentos` deu para esta loja.
 */
export function EquipeDaLoja() {
  const { estabelecimentoId = '' } = useParams();
  const { lojas } = useContextoDoPainel();
  const permissoes =
    lojas.find((uma) => uma.estabelecimentoId === estabelecimentoId)?.permissoes ?? [];
  return <ConvidarParaEquipe estabelecimentoId={estabelecimentoId} minhasPermissoes={permissoes} />;
}

/**
 * O produto aberto. A montagem dos grupos e das opções é do **cardápio**, como a marcação
 * é da disponibilidade (W-J, ADR-079) — e, dentro dele, por permissão: `ALTERAR_PRODUTO`
 * para montar, `DESATIVAR_PRODUTO` para tirar do ar. Conveniência, não segurança.
 */
export function ProdutoDaSecao({ secao }: { secao: Secao }) {
  const { estabelecimentoId = '', produtoId = '' } = useParams();
  const navegar = useNavigate();
  const { lojas } = useContextoDoPainel();
  const permissoes =
    lojas.find((uma) => uma.estabelecimentoId === estabelecimentoId)?.permissoes ?? [];
  const noCardapio = secao === 'cardapio';
  return (
    <ProdutoAberto
      estabelecimentoId={estabelecimentoId}
      produtoId={produtoId}
      podeMarcar={MARCA[secao]}
      podeMontar={noCardapio && permissoes.includes('ALTERAR_PRODUTO')}
      podeTirarDoAr={noCardapio && permissoes.includes('DESATIVAR_PRODUTO')}
      aoVoltar={() => void navegar(caminhoDaSecao(estabelecimentoId, secao))}
    />
  );
}

/** `/painel/lojas/:id/horario` — a semana inteira, lida e salva (ADR-074). */
export function HorarioDaLoja() {
  const { estabelecimentoId = '' } = useParams();
  return <HorarioDaSemana key={estabelecimentoId} estabelecimentoId={estabelecimentoId} />;
}
