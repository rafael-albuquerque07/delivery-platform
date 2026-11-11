package com.deliveryplatform.merchant.application.usecase;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import com.deliveryplatform.merchant.application.exception.OperacaoRecusada;
import com.deliveryplatform.merchant.application.port.in.DefinirDescontoDeRetirada;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.Operacao;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.valuetypes.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * O desconto de retirada (ADR-024, ADR-080). No molde do {@code DefinirHorarioService}:
 * autoriza antes de buscar, e traduz a M15 num ponto só. Não publica nada: quem o lerá é o
 * congelamento em T01, no {@code order}.
 */
@Service
public class DefinirDescontoDeRetiradaService implements DefinirDescontoDeRetirada {

    private final MembroRepositorio membros;
    private final EstabelecimentoRepositorio estabelecimentos;

    public DefinirDescontoDeRetiradaService(MembroRepositorio membros,
                                            EstabelecimentoRepositorio estabelecimentos) {
        this.membros = membros;
        this.estabelecimentos = estabelecimentos;
    }

    @Override
    @Transactional
    public Operacao definir(UUID lojaId, UUID autorUsuarioId, BigDecimal desconto) {
        membros.buscarPorUsuarioELoja(autorUsuarioId, lojaId)
                .filter(vinculo -> vinculo.pode(Permissao.CONFIGURAR_LOJA))
                .orElseThrow(AcessoNegado::new);

        Estabelecimento loja = estabelecimentos.buscarPorId(lojaId).orElseThrow(AcessoNegado::new);

        Estabelecimento comDesconto;
        try {
            comDesconto = loja.comDescontoDeRetirada(Money.de(desconto));
        } catch (IllegalArgumentException recusa) {
            throw new OperacaoRecusada(OperacaoRecusada.Motivo.DESCONTO_NEGATIVO, recusa);
        }
        return estabelecimentos.salvar(comDesconto).getOperacao();
    }
}
