package com.deliveryplatform.merchant.api.dto;

import com.deliveryplatform.merchant.application.port.in.CriarEstabelecimento.NovoEstabelecimento;
import com.deliveryplatform.merchant.domain.model.MetodoPagamento;
import com.deliveryplatform.merchant.domain.model.Modalidade;
import com.deliveryplatform.merchant.domain.model.TipoDeOperacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * O corpo de {@code POST /api/v1/me/estabelecimentos} — quatro coisas e nada mais
 * (ADR-060 §2): o que o agregado exige para a loja nascer.
 *
 * <p><b>Bean Validation cobre a forma</b>: o que falta, o que vem em branco, o mapa
 * vazio. <b>O que só o agregado sabe</b> — documento com 11 ou 14 dígitos, telefone,
 * fuso do conjunto brasileiro (M16), modalidade sem método (M12), fundo negativo — é
 * recusado na construção, e vira 400 por {@code CadastroDeLojaRecusado}. Repetir
 * essas regras aqui seria um segundo lugar onde elas estão escritas.
 */
public record CriarEstabelecimentoRequest(
        @NotNull @Valid IdentificacaoRequest identificacao,
        @NotNull @Valid PoliticaDeTrocoRequest politicaDeTroco,
        @NotNull TipoDeOperacao tipoDeOperacao,
        @NotEmpty Map<Modalidade, Set<MetodoPagamento>> metodosPorModalidade) {

    public record IdentificacaoRequest(
            @NotBlank String nome,
            @NotBlank String documento,
            @NotBlank String telefone,
            @NotBlank String enderecoTextual,
            @NotBlank String bairro,
            @NotBlank String fusoHorario) {
    }

    public record PoliticaDeTrocoRequest(
            @NotNull BigDecimal fundoMaximoDeTroco,
            boolean aceitaPedidoSemTrocoDisponivel) {
    }

    public NovoEstabelecimento paraComando() {
        return new NovoEstabelecimento(
                identificacao.nome(),
                identificacao.documento(),
                identificacao.telefone(),
                identificacao.enderecoTextual(),
                identificacao.bairro(),
                identificacao.fusoHorario(),
                politicaDeTroco.fundoMaximoDeTroco(),
                politicaDeTroco.aceitaPedidoSemTrocoDisponivel(),
                tipoDeOperacao,
                metodosPorModalidade);
    }
}
