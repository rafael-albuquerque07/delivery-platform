package com.deliveryplatform.merchant.application.usecase;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import com.deliveryplatform.merchant.application.port.in.ConsultarOperacaoAtual;
import com.deliveryplatform.merchant.application.port.in.OperacaoAtual;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.Membro;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Confere o vínculo, carrega a loja, e pergunta ao agregado — a forma do
 * {@link ConsultarExpedienteCorrenteService}, e pelos mesmos motivos: o vínculo antes da loja,
 * para que não haja 404; e vínculo ativo apontando para loja inexistente é defeito de dados, 500.
 *
 * <p><b>O {@code aberto} vem do {@code estaAberta}</b>, que compõe horário e pausa num lugar só.
 * Este serviço não olha faixa nem pausa.
 */
@Service
public class ConsultarOperacaoAtualService implements ConsultarOperacaoAtual {

    private final MembroRepositorio membros;
    private final EstabelecimentoRepositorio lojas;
    private final Clock relogio;

    public ConsultarOperacaoAtualService(MembroRepositorio membros,
                                         EstabelecimentoRepositorio lojas,
                                         Clock relogio) {
        this.membros = membros;
        this.lojas = lojas;
        this.relogio = relogio;
    }

    @Override
    @Transactional(readOnly = true)
    public OperacaoAtual de(UUID estabelecimentoId, UUID usuarioId) {
        Estabelecimento loja = lojaDoVinculo(membros, lojas, estabelecimentoId, usuarioId);
        return new OperacaoAtual(
                loja.getId(),
                loja.estaAberta(Instant.now(relogio)),
                loja.getOperacao().tipoDeOperacao());
    }

    /** O confronto da invariante 9 das duas rotas da ADR-087: vínculo ativo, e só ele. */
    static Estabelecimento lojaDoVinculo(MembroRepositorio membros, EstabelecimentoRepositorio lojas,
                                         UUID estabelecimentoId, UUID usuarioId) {
        Membro vinculo = membros.buscarPorUsuarioELoja(usuarioId, estabelecimentoId)
                .filter(Membro::ativo)
                .orElseThrow(AcessoNegado::new);
        return lojas.buscarPorId(vinculo.getEstabelecimentoId())
                .orElseThrow(() -> new IllegalStateException(
                        "vínculo ativo apontando para estabelecimento que não existe: "
                                + vinculo.getEstabelecimentoId()));
    }
}
