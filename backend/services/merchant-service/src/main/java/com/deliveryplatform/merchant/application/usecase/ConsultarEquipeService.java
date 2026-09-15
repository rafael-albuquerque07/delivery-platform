package com.deliveryplatform.merchant.application.usecase;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import com.deliveryplatform.merchant.application.port.in.ConsultarEquipe;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.model.Equipe;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Permissao;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Três linhas de autorização, e cada uma existe por um motivo diferente.
 *
 * <p><b>1. O vínculo é procurado pelo par (usuário, loja).</b> Não se busca a
 * loja e se pergunta quem é o dono; busca-se o vínculo. Quem não tem vínculo
 * naquela loja recebe exatamente a mesma resposta de quem pediu uma loja que
 * não existe — porque a consulta que falha é a mesma.
 *
 * <p><b>2. A permissão é conferida sobre o vínculo, não sobre o token.</b> O
 * token carrega seis claims e nenhum papel (ADR-015 emendada, M2): uma
 * permissão revogada há dez segundos já não vale aqui, enquanto dentro do token
 * ela valeria até expirar.
 *
 * <p><b>3. Falha fechada.</b> Não há caminho alternativo, não há valor padrão,
 * não há "em caso de dúvida, deixa passar". M8 diz que indisponibilidade nega —
 * e o jeito de garantir isso é não escrever o ramo que perdoa.
 *
 * <p><b>Sem cache, e é decisão desta rodada.</b> A ADR-011 prevê cache em
 * processo de 60 s para esta resolução, invalidado por {@code VinculoAlteradoV1}.
 * O evento não existe, o outbox não existe, e cache sem invalidação não é
 * otimização: é permissão revogada continuando a valer por um minuto, em
 * silêncio. Entra junto com o evento, na rodada seguinte.
 */
@Service
public class ConsultarEquipeService implements ConsultarEquipe {

    private final MembroRepositorio membros;

    public ConsultarEquipeService(MembroRepositorio membros) {
        this.membros = membros;
    }

    @Override
    public Equipe daLoja(UUID estabelecimentoId, UUID usuarioAutenticado) {
        Membro meuVinculo = membros
                .buscarPorUsuarioELoja(usuarioAutenticado, estabelecimentoId)
                .orElseThrow(AcessoNegado::new);

        if (!meuVinculo.pode(Permissao.GERENCIAR_EQUIPE)) {
            throw new AcessoNegado();
        }

        return membros.equipeDe(estabelecimentoId);
    }
}
