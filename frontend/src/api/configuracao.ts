import { chamar } from './cliente';
import type { components } from './generated/merchant';

/**
 * Os três irmãos do horário sob `/settings` (ADR-080, ADR-081): o pedido mínimo, o desconto de
 * retirada e as áreas de entrega. Cada um lê e substitui no mesmo caminho, sob
 * `CONFIGURAR_LOJA`, e responde com o que ficou gravado. Os tipos são os **gerados** do
 * contrato do `merchant`; a recusa chega como `ErroDaApi`, e quem a transforma em frase é o
 * `fraseDaRecusa`.
 */
export type PedidoMinimo = components['schemas']['PedidoMinimoResponse'];
export type NovoPedidoMinimo = components['schemas']['PedidoMinimoRequest'];
export type DescontoDeRetirada = components['schemas']['DescontoDeRetiradaResponse'];
export type NovoDescontoDeRetirada = components['schemas']['DescontoDeRetiradaRequest'];
export type AreasDeEntrega = components['schemas']['AreasDeEntregaResponse'];
export type NovasAreasDeEntrega = components['schemas']['AreasDeEntregaRequest'];

function caminhoDaConfiguracao(estabelecimentoId: string, recurso: string): string {
  return `/api/v1/merchants/${estabelecimentoId}/settings/${recurso}`;
}

export const caminhoDoPedidoMinimo = (estabelecimentoId: string) =>
  caminhoDaConfiguracao(estabelecimentoId, 'pedido-minimo');
export const caminhoDoDesconto = (estabelecimentoId: string) =>
  caminhoDaConfiguracao(estabelecimentoId, 'desconto-retirada');
export const caminhoDasAreas = (estabelecimentoId: string) =>
  caminhoDaConfiguracao(estabelecimentoId, 'areas-entrega');

export function definirPedidoMinimo(
  estabelecimentoId: string,
  corpo: NovoPedidoMinimo,
  token: string,
): Promise<PedidoMinimo> {
  return chamar<PedidoMinimo>(caminhoDoPedidoMinimo(estabelecimentoId), {
    metodo: 'PUT',
    corpo,
    token,
  });
}

export function definirDesconto(
  estabelecimentoId: string,
  corpo: NovoDescontoDeRetirada,
  token: string,
): Promise<DescontoDeRetirada> {
  return chamar<DescontoDeRetirada>(caminhoDoDesconto(estabelecimentoId), {
    metodo: 'PUT',
    corpo,
    token,
  });
}

export function definirAreas(
  estabelecimentoId: string,
  corpo: NovasAreasDeEntrega,
  token: string,
): Promise<AreasDeEntrega> {
  return chamar<AreasDeEntrega>(caminhoDasAreas(estabelecimentoId), { metodo: 'PUT', corpo, token });
}
