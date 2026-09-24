package com.deliveryplatform.merchant.application.usecase;

import com.deliveryplatform.merchant.application.exception.AcessoNegado;
import com.deliveryplatform.merchant.application.port.out.MembroRepositorio;
import com.deliveryplatform.merchant.application.port.out.Outbox;
import com.deliveryplatform.merchant.domain.evento.VinculoAlteradoV1;
import com.deliveryplatform.merchant.domain.model.Equipe;
import com.deliveryplatform.merchant.domain.model.Membro;
import com.deliveryplatform.merchant.domain.model.Permissao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/**
 * O dono das operações de escrita da equipe: as sete administrativas da
 * {@link Equipe} — promover, rebaixar, suspender, reativar, remover, sair e
 * alterar permissões — mais o registro do vínculo que nasce pelo aceite.
 *
 * <p><b>Por que ele nasce só agora.</b> Até esta rodada as operações
 * existiam no domínio e <i>não tinham dono na aplicação</i>: quem carregava os
 * membros, aplicava a regra e gravava eram os testes. Isso funcionou enquanto a
 * gravação era de uma coisa só. Deixa de funcionar quando são duas — o
 * {@code Membro} e a linha de outbox — porque a invariante 7 exige que as duas
 * estejam na mesma transação, e não havia transação de negócio em lugar nenhum
 * para pendurá-las.
 *
 * <p><b>Todo o emitir mora num lugar só.</b> As sete operações públicas
 * terminam em {@link #executar}, e é lá — e só lá — que o evento é registrado.
 * A lista das que emitem tem de ser a lista completa das que mudam vínculo: o
 * {@code alterarPermissoes} não constava do rascunho desta rodada e entrou ao
 * conferir os métodos públicos da {@link Equipe}.
 * Uma oitava operação escrita amanhã atravessa o mesmo funil ou não compila: não
 * há como aplicar uma regra de equipe por aqui sem passar pelo ponto que
 * publica. Esquecer de emitir deixa de ser possível por construção, em vez de
 * depender de alguém lembrar.
 *
 * <p><b>Por que o cadeado em todas, e não só em quem conta administrador.</b> O
 * cadeado da B1 sobre a linha do estabelecimento nasceu para o {@code sair}
 * concorrente. Mas quase todas mexem na contagem de administradores ativos —
 * promover soma, rebaixar tira, suspender tira, reativar soma, remover tira,
 * sair tira, e o aceite cria vínculo —, e alterar permissões é conferido por A2
 * contra o que o autor tem <i>naquele instante</i>. Tomar o mesmo cadeado em
 * todas torna a equipe serializável sobre a superfície inteira de escrita, em
 * vez de sobre uma operação. E é barato: o cadeado é por estabelecimento, e
 * escrita de equipe é rara.
 *
 * <p>O cadeado vem de {@link MembroRepositorio#equipeParaAlteracao}, que a B1
 * desenhou para travar <b>e</b> ler numa chamada só — cadeado primeiro, leitura
 * depois, e nunca separados por quem chama.
 */
@Service
public class GerenciarEquipeService {

    private final MembroRepositorio membros;
    private final Outbox outbox;
    private final Clock relogio;

    public GerenciarEquipeService(MembroRepositorio membros, Outbox outbox, Clock relogio) {
        this.membros = membros;
        this.outbox = outbox;
        this.relogio = relogio;
    }

    // ── as sete ─────────────────────────────────────────────────────────────

    @Transactional
    public Membro promover(UUID lojaId, UUID autorUsuarioId, UUID alvoUsuarioId) {
        return executar(lojaId, (equipe, agora) -> {
            Membro alvo = daEquipe(equipe, alvoUsuarioId);
            equipe.promover(daEquipe(equipe, autorUsuarioId), alvo, agora);
            return alvo;
        });
    }

    @Transactional
    public Membro rebaixar(UUID lojaId, UUID autorUsuarioId, UUID alvoUsuarioId) {
        return executar(lojaId, (equipe, agora) -> {
            Membro alvo = daEquipe(equipe, alvoUsuarioId);
            equipe.rebaixar(daEquipe(equipe, autorUsuarioId), alvo, agora);
            return alvo;
        });
    }

    @Transactional
    public Membro suspender(UUID lojaId, UUID autorUsuarioId, UUID alvoUsuarioId) {
        return executar(lojaId, (equipe, agora) -> {
            Membro alvo = daEquipe(equipe, alvoUsuarioId);
            equipe.suspender(daEquipe(equipe, autorUsuarioId), alvo, agora);
            return alvo;
        });
    }

    @Transactional
    public Membro reativar(UUID lojaId, UUID autorUsuarioId, UUID alvoUsuarioId) {
        return executar(lojaId, (equipe, agora) -> {
            Membro alvo = daEquipe(equipe, alvoUsuarioId);
            equipe.reativar(daEquipe(equipe, autorUsuarioId), alvo, agora);
            return alvo;
        });
    }

    @Transactional
    public Membro remover(UUID lojaId, UUID autorUsuarioId, UUID alvoUsuarioId) {
        return executar(lojaId, (equipe, agora) -> {
            Membro alvo = daEquipe(equipe, alvoUsuarioId);
            equipe.remover(daEquipe(equipe, autorUsuarioId), alvo, agora);
            return alvo;
        });
    }

    /**
     * A única sem autor separado do alvo — e por isso a única em que a A3 volta a
     * ser checagem em vez de teorema (B1).
     */
    @Transactional
    public Membro sair(UUID lojaId, UUID quemSaiUsuarioId) {
        return executar(lojaId, (equipe, agora) -> {
            Membro quemSai = daEquipe(equipe, quemSaiUsuarioId);
            equipe.sair(quemSai, agora);
            return quemSai;
        });
    }

    /**
     * A sétima: conceder e revogar permissão avulsa. Muda o vínculo tanto quanto
     * as outras seis — e um consumidor que não soubesse de uma revogação seguiria
     * autorizando com a lista velha até o fim do TTL do cache.
     */
    @Transactional
    public Membro alterarPermissoes(
            UUID lojaId, UUID autorUsuarioId, UUID alvoUsuarioId, Set<Permissao> novas) {
        return executar(lojaId, (equipe, agora) -> {
            Membro alvo = daEquipe(equipe, alvoUsuarioId);
            equipe.alterarPermissoes(daEquipe(equipe, autorUsuarioId), alvo, novas, agora);
            return alvo;
        });
    }

    /**
     * Registra no outbox um vínculo que nasceu por fora desta classe.
     *
     * <p>Existe por causa do aceite de convite, a única escrita de equipe cuja
     * regra não mora na {@link Equipe} sozinha — ela precisa do
     * {@code Convite}. Em vez de duplicar o funil, o
     * {@code AceitarConviteService} chama este método <b>dentro da mesma
     * transação</b> em que gravou o {@code Membro}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarVinculoNascido(Membro novo, Instant agora) {
        outbox.registrar(VinculoAlteradoV1.de(novo, agora));
    }

    // ── o funil ─────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Operacao {
        /** Aplica a regra e devolve <b>o membro cujo vínculo mudou</b>. */
        Membro aplicar(Equipe equipe, Instant agora);
    }

    private Membro executar(UUID lojaId, Operacao operacao) {
        // Truncado em microssegundos porque é a precisão do timestamptz: sem
        // isso o instante gravado e o instante do payload divergem no último
        // dígito, e o consumidor compara ocorridoEm para descartar evento velho.
        Instant agora = Instant.now(relogio).truncatedTo(ChronoUnit.MICROS);

        Equipe equipe = membros.equipeParaAlteracao(lojaId);
        Membro alterado = operacao.aplicar(equipe, agora);

        membros.salvar(alterado);
        outbox.registrar(VinculoAlteradoV1.de(alterado, agora));
        return alterado;
    }

    /**
     * Acha o membro <b>dentro da equipe já carregada</b>, e não com uma segunda
     * consulta.
     *
     * <p>Não é economia de query: é identidade. As regras da {@link Equipe}
     * confrontam o autor e o alvo com os membros que ela guarda, e um objeto
     * lido por fora seria outra instância do mesmo vínculo — igual em conteúdo e
     * estranho para a equipe.
     *
     * <p><b>Ausente vira {@code AcessoNegado}, e não "não encontrado".</b> Pela
     * mesma razão de M7 que a C-A provou comparando corpos de resposta: quem não
     * tem vínculo e quem procura alguém que não existe têm de receber a mesma
     * coisa, ou a rota vira um scanner de equipes escrito em códigos de status.
     */
    private Membro daEquipe(Equipe equipe, UUID usuarioId) {
        return equipe.getMembros().stream()
                .filter(membro -> membro.getUsuarioId().equals(usuarioId))
                .findFirst()
                .orElseThrow(AcessoNegado::new);
    }
}
