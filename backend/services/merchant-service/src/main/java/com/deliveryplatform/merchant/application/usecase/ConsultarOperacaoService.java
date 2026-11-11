package com.deliveryplatform.merchant.application.usecase;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import com.deliveryplatform.merchant.application.port.in.ConsultarOperacao;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Operacao;
import com.deliveryplatform.merchant.domain.model.Permissao;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * A leitura do pedido mínimo e do desconto de retirada (ADR-080), no molde do
 * {@code ConsultarHorarioService}: <b>autoriza antes de buscar</b>, com a mesma permissão da
 * escrita; sem vínculo ativo com {@code CONFIGURAR_LOJA} é o 403 único (M7), loja que não
 * existe inclusive (ADR-056).
 */
@Service
public class ConsultarOperacaoService implements ConsultarOperacao {

    private final MembroRepositorio membros;
    private final EstabelecimentoRepositorio estabelecimentos;

    public ConsultarOperacaoService(MembroRepositorio membros,
                                    EstabelecimentoRepositorio estabelecimentos) {
        this.membros = membros;
        this.estabelecimentos = estabelecimentos;
    }

    @Override
    public Operacao daLoja(UUID lojaId, UUID autorUsuarioId) {
        membros.buscarPorUsuarioELoja(autorUsuarioId, lojaId)
                .filter(vinculo -> vinculo.pode(Permissao.CONFIGURAR_LOJA))
                .orElseThrow(AcessoNegado::new);

        return estabelecimentos.buscarPorId(lojaId)
                .orElseThrow(AcessoNegado::new)
                .getOperacao();
    }
}
