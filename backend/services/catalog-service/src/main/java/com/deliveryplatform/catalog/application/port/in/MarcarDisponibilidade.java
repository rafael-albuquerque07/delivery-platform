package com.deliveryplatform.catalog.application.port.in;

import com.deliveryplatform.catalog.domain.model.EstadoDeDisponibilidade;
import com.deliveryplatform.catalog.domain.model.Produto;

import java.util.UUID;

/**
 * "Acabou a calabresa" — o primeiro ato deste sistema que <b>muda o mundo</b>.
 *
 * <p>Tudo o que existe até aqui responde. Esta é a primeira rota de escrita do
 * {@code catalog}, e é a que cria o {@code ESGOTADO_HOJE} sobre o qual a
 * reativação da G-C3 vai operar. Sem ela, aquele consumidor teria apenas dado
 * de teste para processar.
 *
 * <h2>O cliente diz <i>o quê</i>; o servidor diz <i>quando</i></h2>
 *
 * <p>Entra um estado, e só. O {@code marcadoEm} é o relógio do serviço e o
 * {@code expedienteDeReferencia} vem do {@code merchant} — <b>nenhum dos dois
 * chega pelo corpo</b>. É a mesma razão pela qual o preço não vem do carrinho:
 * data que o cliente escolhe é data que o cliente escolhe errado, e aqui errar
 * a data significa um produto que volta ao cardápio no meio do pico ou que
 * nunca volta.
 *
 * <h2>Devolve o produto, e não 204</h2>
 *
 * <p>Marcar <b>uma opção</b> como esgotada pode tornar o <b>produto</b> não
 * vendável: é a terceira cláusula do {@code vendavel}, a que ninguém lembra —
 * um grupo obrigatório sem nenhuma opção disponível não tem seleção possível.
 * O cliente não consegue derivar isso, porque o resumo não carrega os grupos.
 *
 * <p>Então a resposta traz o produto inteiro, já recalculado. Com 204, a tela
 * teria de recarregar a lista para descobrir uma consequência que o próprio ato
 * acabou de causar.
 *
 * @see com.deliveryplatform.catalog.application.port.out.ExpedienteCorrentePort
 */
public interface MarcarDisponibilidade {

    Produto deProduto(UUID estabelecimentoId, UUID produtoId, EstadoDeDisponibilidade novo);

    Produto deOpcao(UUID estabelecimentoId, UUID produtoId, UUID grupoId, UUID opcaoId,
                    EstadoDeDisponibilidade novo);
}
