package com.deliveryplatform.merchant.application.usecase;

import com.deliveryplatform.merchant.application.exception.CondicoesRecusadas;
import com.deliveryplatform.merchant.application.port.in.CondicoesDoPedido;
import com.deliveryplatform.merchant.application.port.in.ConsultarCondicoesDoPedido;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.AreaDeEntrega;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.PoliticaDeTroco;
import com.deliveryplatform.valuetypes.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * As condições do pedido (ADR-087). Vínculo antes de tudo, como as outras rotas internas; depois
 * as perguntas ao agregado, que já tem cada resposta.
 *
 * <p><b>A área vem do {@code areaPorNome} ou do {@code areaPara}</b>, que só olham áreas ativas
 * (ADR-020) e devolvem vazio quando não há — nunca uma taxa zero por falta de resposta (M11). A
 * lista de bairros da não atendida também é só das ativas.
 */
@Service
public class ConsultarCondicoesDoPedidoService implements ConsultarCondicoesDoPedido {

    private final MembroRepositorio membros;
    private final EstabelecimentoRepositorio lojas;

    public ConsultarCondicoesDoPedidoService(MembroRepositorio membros,
                                             EstabelecimentoRepositorio lojas) {
        this.membros = membros;
        this.lojas = lojas;
    }

    @Override
    @Transactional(readOnly = true)
    public CondicoesDoPedido de(UUID estabelecimentoId, UUID usuarioId,
                                Modalidade modalidade, String bairro, String cep) {
        Estabelecimento loja = ConsultarOperacaoAtualService.lojaDoVinculo(
                membros, lojas, estabelecimentoId, usuarioId);
        UUID id = loja.getId();

        if (!loja.aceita(modalidade)) {
            return CondicoesDoPedido.naoAceita(id, modalidade);
        }
        Set<MetodoPagamento> metodos = loja.metodosDe(modalidade);
        Money minimo = loja.pedidoMinimoDe(modalidade);
        PoliticaDeTroco troco = loja.getPoliticaDeTroco();

        if (modalidade == Modalidade.RETIRADA) {
            return CondicoesDoPedido.retirada(
                    id, metodos, minimo, loja.getOperacao().descontoDeRetirada(), troco);
        }

        return area(loja, bairro, cep)
                .map(area -> CondicoesDoPedido.entregaAtendida(
                        id, metodos, minimo, troco, area.nome(), area.taxa()))
                .orElseGet(() -> CondicoesDoPedido.entregaNaoAtendida(
                        id, metodos, minimo, troco, bairrosAtendidos(loja)));
    }

    /** O bairro manda; o CEP é a alternativa (ADR-020). */
    private static Optional<AreaDeEntrega> area(Estabelecimento loja, String bairro, String cep) {
        if (bairro != null && !bairro.isBlank()) {
            return loja.areaPorNome(bairro);
        }
        if (cep == null || cep.isBlank()) {
            throw new CondicoesRecusadas(CondicoesRecusadas.Motivo.ENDERECO_AUSENTE);
        }
        try {
            return loja.areaPara(cep);
        } catch (IllegalArgumentException cepInvalido) {
            throw new CondicoesRecusadas(CondicoesRecusadas.Motivo.CEP_INVALIDO, cepInvalido);
        }
    }

    private static List<String> bairrosAtendidos(Estabelecimento loja) {
        return loja.getAreasDeEntrega().stream()
                .filter(AreaDeEntrega::ativa)
                .sorted(Comparator.comparing(AreaDeEntrega::identificadorNormalizado))
                .map(AreaDeEntrega::nome)
                .toList();
    }
}
