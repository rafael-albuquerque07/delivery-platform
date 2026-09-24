package com.deliveryplatform.merchant.application.usecase;

import com.deliveryplatform.merchant.application.port.out.ConviteRepositorio;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.domain.exception.ConviteInvalido;
import com.deliveryplatform.merchant.domain.model.Convite;
import com.deliveryplatform.merchant.domain.model.Equipe;
import com.deliveryplatform.merchant.domain.model.Membro;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * O vínculo que nasce por convite.
 *
 * <p>É a única escrita de equipe cuja regra não cabe dentro da {@link Equipe}
 * sozinha, porque precisa do {@link Convite} — e por isso não atravessa o funil
 * do {@link GerenciarEquipeService}. O que ela <b>não</b> faz é duplicar o
 * funil: chama {@link GerenciarEquipeService#registrarVinculoNascido}, que é
 * {@code MANDATORY}, de dentro desta transação. Se algum dia alguém a chamar
 * fora de transação, o Spring recusa em vez de gravar um evento solto.
 *
 * <p>O cadeado do estabelecimento é tomado aqui pelo mesmo motivo das outras
 * escritas: o aceite cria vínculo, e a A2 é conferida de novo neste instante
 * (B2) — entre a emissão e o aceite o convidante pode ter perdido a permissão,
 * sido suspenso ou saído.
 */
@Service
public class AceitarConviteService {

    private final ConviteRepositorio convites;
    private final MembroRepositorio membros;
    private final GerenciarEquipeService equipes;
    private final Clock relogio;

    public AceitarConviteService(ConviteRepositorio convites,
                                 MembroRepositorio membros,
                                 GerenciarEquipeService equipes,
                                 Clock relogio) {
        this.convites = convites;
        this.membros = membros;
        this.equipes = equipes;
        this.relogio = relogio;
    }

    @Transactional
    public Membro aceitar(String token, UUID usuarioId) {
        Instant agora = Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);

        // Token desconhecido recebe a mesma recusa que token expirado, aceito ou
        // cancelado — a ConviteInvalido que o próprio Convite lança. Uma exceção
        // diferente aqui diria a quem adivinha se o token existe (B2).
        Convite convite = convites.buscarPorToken(token).orElseThrow(ConviteInvalido::new);

        Equipe equipe = membros.equipeParaAlteracao(convite.getEstabelecimentoId());
        Membro novo = equipe.aceitar(convite, token, usuarioId, agora);

        membros.salvar(novo);
        convites.salvar(convite);
        equipes.registrarVinculoNascido(novo, agora);
        return novo;
    }
}
