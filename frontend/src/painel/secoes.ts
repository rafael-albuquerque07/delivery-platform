import type { Permissao } from './tipos';

/**
 * As seções do painel e a tabela que liga cada permissão à sua tela — fora do
 * arquivo do componente, pelo mesmo motivo que tirou o contexto da sessão do
 * provider na W-A: o fast refresh só recarrega módulo que exporta apenas
 * componentes. O porquê da tabela está no javadoc do `MenuDoPainel`.
 */
export type Secao = 'cardapio' | 'disponibilidade';

const ITENS: Record<Permissao, { rotulo: string; secao: Secao | null }> = {
  VER_PRODUTO: { rotulo: 'Cardápio', secao: 'cardapio' },
  CRIAR_PRODUTO: { rotulo: 'Novo produto', secao: null },
  ALTERAR_PRODUTO: { rotulo: 'Disponibilidade', secao: 'disponibilidade' },
  DESATIVAR_PRODUTO: { rotulo: 'Despublicar', secao: null },
  VER_PEDIDO: { rotulo: 'Pedidos', secao: null },
  ALTERAR_STATUS: { rotulo: 'Andamento', secao: null },
  VER_VENDAS: { rotulo: 'Vendas', secao: null },
  VER_ENTREGA: { rotulo: 'Entregas', secao: null },
  GERENCIAR_EQUIPE: { rotulo: 'Equipe', secao: null },
  GERENCIAR_JORNADA: { rotulo: 'Jornada', secao: null },
};

export function secoesDe(permissoes: Permissao[]): { rotulo: string; secao: Secao }[] {
  // Set: o servidor guarda as permissões num EnumSet e não repete, mas duas
  // linhas iguais aqui colidiriam na `key` do React — e chave repetida é
  // silenciosa até virar item fantasma numa re-renderização.
  return [...new Set(permissoes)]
    .map((permissao) => ITENS[permissao])
    .filter((item): item is { rotulo: string; secao: Secao } => Boolean(item?.secao));
}
