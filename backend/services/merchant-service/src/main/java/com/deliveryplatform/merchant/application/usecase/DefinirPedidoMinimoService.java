package com.deliveryplatform.merchant.application.usecase;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import com.deliveryplatform.merchant.application.exception.OperacaoRecusada;
import com.deliveryplatform.merchant.application.port.in.DefinirPedidoMinimo;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.Operacao;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.valuetypes.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Os pedidos mínimos, substituídos inteiros (ADR-028, ADR-080). No molde do
 * {@code DefinirHorarioService}: autoriza antes de buscar, e traduz a recusa do domínio num
 * ponto só, em volta da construção.
 *
 * <p><b>O caso de uso não confere a M17</b> — nem a modalidade que falta, nem o mínimo
 * negativo, nem o zero. Quem confere é o construtor do {@code Operacao}; aqui só se troca a
 * exceção dele pela que o tratador sabe dizer.
 *
 * <p><b>Não publica nada.</b> Ninguém consome o mínimo: quem o lerá é a guarda de T01, no
 * {@code order}, que não tem código (ADR-028 §3).
 */
@Service
public class DefinirPedidoMinimoService implements DefinirPedidoMinimo {

    private final MembroRepositorio membros;
    private final EstabelecimentoRepositorio estabelecimentos;

    public DefinirPedidoMinimoService(MembroRepositorio membros,
                                      EstabelecimentoRepositorio estabelecimentos) {
        this.membros = membros;
        this.estabelecimentos = estabelecimentos;
    }

    @Override
    @Transactional
    public Operacao definir(UUID lojaId, UUID autorUsuarioId, Map<Modalidade, BigDecimal> minimos) {
        membros.buscarPorUsuarioELoja(autorUsuarioId, lojaId)
                .filter(vinculo -> vinculo.pode(Permissao.CONFIGURAR_LOJA))
                .orElseThrow(AcessoNegado::new);

        Estabelecimento loja = estabelecimentos.buscarPorId(lojaId).orElseThrow(AcessoNegado::new);

        Map<Modalidade, Money> emReais = new EnumMap<>(Modalidade.class);
        minimos.forEach((modalidade, valor) -> emReais.put(modalidade, Money.de(valor)));

        Estabelecimento comMinimos;
        try {
            comMinimos = loja.comPedidoMinimo(emReais);
        } catch (IllegalArgumentException recusa) {
            throw new OperacaoRecusada(OperacaoRecusada.Motivo.PEDIDO_MINIMO, recusa);
        }
        return estabelecimentos.salvar(comMinimos).getOperacao();
    }
}
