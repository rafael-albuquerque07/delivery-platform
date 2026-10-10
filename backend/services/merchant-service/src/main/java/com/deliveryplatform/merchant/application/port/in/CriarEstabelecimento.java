package com.deliveryplatform.merchant.application.port.in;

import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.TipoDeOperacao;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cria uma loja e o vínculo de fundador de quem a cria, na mesma transação
 * (ADR-060). É a primeira escrita de domínio do {@code merchant} que um cliente
 * alcança.
 */
public interface CriarEstabelecimento {

    /**
     * @param fundador o portador do token — vira {@code ADMINISTRADOR} da loja criada
     * @return a loja na forma que {@code GET /api/v1/me/estabelecimentos} devolve
     */
    LojaDoUsuario criar(UUID fundador, NovoEstabelecimento pedido);

    /**
     * O que o corpo traz, ainda cru: os objetos de valor do domínio nascem dentro do
     * serviço, num ponto só, e é lá que a recusa deles vira 400 (ADR-060).
     *
     * <p>Só o que o agregado exige (M12, ADR-060 §2). Horário e áreas não estão aqui
     * — a loja nasce sem os dois —, e mínimos e desconto nascem em zero por
     * {@code Operacao.nova}.
     */
    record NovoEstabelecimento(
            String nome,
            String documento,
            String telefone,
            String enderecoTextual,
            String bairro,
            String fusoHorario,
            BigDecimal fundoMaximoDeTroco,
            boolean aceitaPedidoSemTrocoDisponivel,
            TipoDeOperacao tipoDeOperacao,
            Map<Modalidade, Set<MetodoPagamento>> metodosPorModalidade) {
    }
}
