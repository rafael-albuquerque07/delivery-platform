package com.deliveryplatform.merchant.application.usecase;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import com.deliveryplatform.merchant.application.exception.AreasRecusadas;
import com.deliveryplatform.merchant.application.exception.AreasRecusadas.Motivo;
import com.deliveryplatform.merchant.application.port.in.DefinirAreasDeEntrega;
import com.deliveryplatform.merchant.application.port.out.EstabelecimentoRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.AreaDeEntrega;
import com.deliveryplatform.merchant.domain.model.Estabelecimento;
import com.deliveryplatform.merchant.domain.model.FaixaDeCep;
import com.deliveryplatform.merchant.domain.model.Permissao;
import com.deliveryplatform.valuetypes.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * As áreas de entrega, substituídas inteiras (ADR-020, ADR-081). No molde do
 * {@code DefinirHorarioService}: autoriza antes de buscar, e traduz as recusas do domínio num
 * ponto só, em volta das três construções que podem recusar — a faixa, a área e a loja.
 *
 * <p><b>O caso de uso não confere regra nenhuma.</b> A M9 e a M10 são do construtor do
 * {@code Estabelecimento}; a E3 e a faixa repetida, do {@code AreaDeEntrega}; o CEP, do
 * {@code FaixaDeCep}. Aqui só se troca a exceção de cada um pela que o tratador sabe dizer.
 *
 * <p><b>Substituir inteiro é seguro porque ninguém guarda o identificador de uma área</b>: o
 * pedido congela {@code nomeAreaSnapshot} e {@code taxaSnapshot} por valor (ADR-020,
 * {@code pedido.md}). <b>E não publica nada</b>: o {@code AreasDeEntregaAlteradasV1} está
 * declarado para o {@code conversation}, que não tem código — a ADR-081 registra isso.
 */
@Service
public class DefinirAreasDeEntregaService implements DefinirAreasDeEntrega {

    private final MembroRepositorio membros;
    private final EstabelecimentoRepositorio estabelecimentos;

    public DefinirAreasDeEntregaService(MembroRepositorio membros,
                                        EstabelecimentoRepositorio estabelecimentos) {
        this.membros = membros;
        this.estabelecimentos = estabelecimentos;
    }

    @Override
    @Transactional
    public List<AreaDeEntrega> definir(UUID lojaId, UUID autorUsuarioId, List<AreaPedida> pedidas) {
        membros.buscarPorUsuarioELoja(autorUsuarioId, lojaId)
                .filter(vinculo -> vinculo.pode(Permissao.CONFIGURAR_LOJA))
                .orElseThrow(AcessoNegado::new);

        List<AreaDeEntrega> areas = new ArrayList<>();
        for (AreaPedida pedida : pedidas) {
            areas.add(area(pedida));
        }
        Estabelecimento loja = estabelecimentos.buscarPorId(lojaId).orElseThrow(AcessoNegado::new);

        Estabelecimento comAreas;
        try {
            comAreas = loja.comAreasDeEntrega(areas);
        } catch (IllegalArgumentException conflito) {
            throw new AreasRecusadas(Motivo.AREAS_EM_CONFLITO, conflito);
        }
        return estabelecimentos.salvar(comAreas).getAreasDeEntrega();
    }

    private static AreaDeEntrega area(AreaPedida pedida) {
        List<FaixaDeCep> faixas = new ArrayList<>();
        for (FaixaPedida faixa : pedida.faixasDeCep()) {
            try {
                faixas.add(FaixaDeCep.de(faixa.inicio(), faixa.fim()));
            } catch (IllegalArgumentException invalido) {
                throw new AreasRecusadas(Motivo.CEP_INVALIDO, invalido);
            }
        }
        try {
            return new AreaDeEntrega(pedida.nome(), faixas, Money.de(pedida.taxa()), pedida.ativa());
        } catch (IllegalArgumentException invalida) {
            throw new AreasRecusadas(Motivo.AREA_INVALIDA, invalida);
        }
    }
}
