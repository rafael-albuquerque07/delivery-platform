package com.deliveryplatform.merchant.api.controller;

import com.deliveryplatform.merchant.api.dto.EquipeResponse;
import com.deliveryplatform.merchant.api.dto.MembroResponse;
import com.deliveryplatform.merchant.api.seguranca.SujeitoDoToken;
import com.deliveryplatform.merchant.application.port.in.ConsultarEquipe;
import com.deliveryplatform.merchant.domain.model.EstadoDoMembro;
import com.deliveryplatform.merchant.domain.model.Membro;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A primeira rota do {@code merchant-service}. Até esta classe existir, o
 * serviço tinha agregado, cinco migrations, 203 testes — e {@code api/} vazia.
 *
 * <p><b>O caminho é o que a ADR-012 já reservou no gateway</b>:
 * {@code /api/v1/merchants/{estabelecimentoId}/team/**} está lá desde o commit
 * inicial, roteando para cá. Nenhuma linha nova de gateway nesta rodada — e o
 * {@code application.yml} de lá abre com um aviso de que aquelas rotas nunca
 * foram verificadas com o gateway no ar, o que continua verdade e continua
 * sendo requisito do marco 1.
 *
 * <p><b>O {@code estabelecimentoId} da URL é entrada, não contexto.</b> Ele não
 * vira filtro de consulta nenhuma antes de passar pelo caso de uso, que o
 * confronta com o vínculo do {@code sub}. É a invariante 9, e é aqui que ela
 * se materializa.
 */
@RestController
@RequestMapping("/api/v1/merchants/{estabelecimentoId}/team")
public class EquipeController {

    private final ConsultarEquipe equipes;

    public EquipeController(ConsultarEquipe equipes) {
        this.equipes = equipes;
    }

    /**
     * <b>Quem foi removido não aparece.</b> A linha continua na tabela — o
     * vínculo é registro de quem teve acesso e quando —, mas isto é a equipe,
     * não o histórico dela. Suspensos aparecem, porque suspensão é reversível e
     * quem administra precisa enxergar quem está de fora para poder trazer de
     * volta.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lista a equipe do estabelecimento",
            description = "Exige vínculo ativo com GERENCIAR_EQUIPE. "
                    + "Sem vínculo, sem permissão e loja inexistente devolvem a mesma recusa.")
    public EquipeResponse listar(
            @PathVariable UUID estabelecimentoId, @AuthenticationPrincipal Jwt token) {

        UUID usuario = SujeitoDoToken.de(token);

        List<MembroResponse> membros = equipes.daLoja(estabelecimentoId, usuario)
                .getMembros()
                .stream()
                .filter(membro -> membro.getEstado() != EstadoDoMembro.REMOVIDO)
                .map(EquipeController::paraResposta)
                .toList();

        return new EquipeResponse(estabelecimentoId, membros);
    }

    private static MembroResponse paraResposta(Membro membro) {
        return new MembroResponse(
                membro.getId(),
                membro.getUsuarioId(),
                membro.getPapel(),
                membro.getEstado(),
                membro.getPermissoes(),
                membro.getCriadoEm());
    }
}
