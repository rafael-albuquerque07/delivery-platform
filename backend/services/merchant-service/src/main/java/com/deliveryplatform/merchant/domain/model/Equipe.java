package com.deliveryplatform.merchant.domain.model;

import com.deliveryplatform.merchant.domain.exception.ConviteInvalido;
import com.deliveryplatform.merchant.domain.exception.JaPertenceAEquipe;
import com.deliveryplatform.merchant.domain.exception.LojaFicariaSemAdministrador;
import com.deliveryplatform.merchant.domain.exception.SemAutoridadeSobreMembro;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Todos os vínculos de uma loja, vistos de uma vez.
 *
 * <p><b>Por que esta classe existe.</b> A1 e A2 são regras sobre <i>dois</i>
 * membros, e cabem no {@link Membro}. A3 é sobre todos eles, e nenhum membro
 * isolado sabe se é o último — cada um é raiz própria, e não há agregado que a
 * hospede. É o mesmo raciocínio pelo qual M9 e M10 vivem dentro do
 * {@code Estabelecimento} e não dentro da {@code AreaDeEntrega}: só quem
 * enxerga todas de uma vez consegue verificar.
 *
 * <p>E A3 tem <b>dois regimes</b>. Nas seis
 * operações administrativas ela não precisa ser verificada: decorre de A1. Em
 * {@link #sair}, precisa — e é a única que a verifica. A demonstração e o
 * limite dela estão em {@link #administradoresAtivos()}.
 *
 * <p><b>Ela não é persistida.</b> É montada a partir do repositório, vive o
 * tempo de uma operação e morre. Persistir "a equipe" criaria uma terceira
 * fonte da mesma verdade, que teria de concordar com as linhas de
 * {@code membro} para sempre.
 *
 * <p><b>É a única entrada para alterar um vínculo</b>, e isso é garantido pelo
 * compilador, não por disciplina: os mutadores do {@link Membro} são
 * pacote-privados, e esta classe é a vizinha de pacote que os alcança. Sem
 * isso existiria um caminho para suspender um administrador sem passar por A1 —
 * e seria o caminho mais curto, portanto o que alguém usaria.
 *
 * <p><b>O que ela não garante sozinha.</b> Duas operações simultâneas leem
 * equipes diferentes, cada uma aprova a sua por A1, e as duas escrevem. Quem
 * fecha esse vão é o cadeado na linha do estabelecimento, tomado pelo
 * {@code MembroRepositorio.equipeParaAlteracao} antes de montar esta lista: a
 * invariante é da loja, então o cadeado é a linha da loja.
 */
public final class Equipe {

    private final UUID estabelecimentoId;
    private final List<Membro> membros;

    private Equipe(UUID estabelecimentoId, List<Membro> membros) {
        this.estabelecimentoId = Objects.requireNonNull(estabelecimentoId, "estabelecimentoId");
        Objects.requireNonNull(membros, "membros");

        for (Membro membro : membros) {
            Objects.requireNonNull(membro, "membro");
            if (!membro.getEstabelecimentoId().equals(estabelecimentoId)) {
                throw new IllegalArgumentException(
                        "membro de outro estabelecimento na equipe: " + membro.getId());
            }
        }
        this.membros = new ArrayList<>(membros);
    }

    public static Equipe de(UUID estabelecimentoId, List<Membro> membros) {
        return new Equipe(estabelecimentoId, membros);
    }

    // ── operações ───────────────────────────────────────────────────────────

    /**
     * Retira o acesso sem apagar o vínculo. As permissões guardadas ficam como
     * estavam — é o que permite reativar alguém do jeito que ele era.
     */
    public void suspender(Membro autor, Membro alvo, Instant agora) {
        autorizar(autor, alvo);
        alvo.suspender(agora);
    }

    /**
     * Devolve o acesso — inclusive a quem foi removido, que é a recontratação.
     * O {@code UNIQUE (usuario_id, estabelecimento_id)} não deixaria criar um
     * segundo vínculo para a mesma pessoa na mesma loja de qualquer jeito, e
     * reusar o que existe é o que "não se apaga ninguém para limpar" implica.
     */
    public void reativar(Membro autor, Membro alvo, Instant agora) {
        autorizar(autor, alvo);
        alvo.reativar(agora);
    }

    public void remover(Membro autor, Membro alvo, Instant agora) {
        autorizar(autor, alvo);
        alvo.remover(agora);
    }

    /**
     * Promoção é ato separado, <b>feito por um administrador existente</b> — a
     * frase é do documento, e é o que impede um gerente de fabricar um par que
     * o alcance.
     */
    public void promover(Membro autor, Membro alvo, Instant agora) {
        autorizar(autor, alvo);
        if (!autor.ehAdministrador()) {
            throw new SemAutoridadeSobreMembro("só um ADMINISTRADOR promove a ADMINISTRADOR");
        }
        alvo.promover(agora);
    }

    public void rebaixar(Membro autor, Membro alvo, Instant agora) {
        autorizar(autor, alvo);
        alvo.rebaixar(agora);
    }

    /**
     * <b>Sair da própria loja</b> — a única operação da equipe que não tem
     * autor separado do alvo, e por isso a única que precisa contar
     * administradores.
     *
     * <p><b>Não passa por A1, e não é esquecimento.</b> A1 é sobre autoridade
     * sobre <i>outra</i> pessoa, e aqui não há outra pessoa. Exigir
     * {@code GERENCIAR_EQUIPE} para sair transformaria a permissão de
     * administrar o time em permissão de ir embora — e a atendente que só tem
     * {@code VER_PEDIDO} ficaria presa a uma loja onde não trabalha mais.
     *
     * <p><b>Aqui A3 é checagem de verdade.</b> É a porta por onde a loja
     * ficaria órfã: o último administrador ativo decidindo ir embora não tem
     * ninguém acima para barrá-lo, e nenhuma outra operação chega nessa
     * situação. Quem é o último promove alguém antes — e a mensagem diz isso,
     * em vez de recusar sem explicar.
     *
     * <p><b>Quem está suspenso pode sair.</b> A suspensão é ato da loja; sair é
     * ato da pessoa, e uma coisa não cancela a outra. Um administrador suspenso
     * já não conta para A3, então a saída dele nunca é a que deixa a loja sem
     * ninguém — se contasse, a loja já estaria órfã antes de ele se mexer.
     *
     * <p><b>O estado é o mesmo {@code REMOVIDO} de quem foi removido</b>, e a
     * diferença entre as duas coisas não vira estado novo: ninguém a lê. Ela
     * pertence ao {@code motivo} do {@code VinculoAlteradoV1}, do mesmo jeito
     * que o {@code ExpedienteAlteradoV1} carrega {@code ABERTURA_DE_EXPEDIENTE}
     * em vez de o catálogo inventar um estado para isso. Um quarto valor no
     * enum obrigaria todo filtro do sistema a tratá-lo para sempre, para
     * distinguir duas coisas que, hoje, ninguém distingue.
     */
    public void sair(Membro quemSai, Instant agora) {
        exigirDaEquipe(quemSai, "quemSai");

        if (quemSai.getEstado() == EstadoDoMembro.REMOVIDO) {
            throw new IllegalStateException("este vínculo já não está ativo nesta loja");
        }
        if (quemSai.ehAdministradorAtivo() && administradoresAtivos() == 1) {
            throw new LojaFicariaSemAdministrador();
        }

        quemSai.remover(agora);
    }

    /**
     * A2 nas duas direções, e é a diferença simétrica que se confere.
     *
     * <p>O que entra precisa estar com o autor — senão ele concede o que não
     * tem. O que sai também — senão um gerente sem {@code VER_VENDAS} rebaixa
     * colegas até o conjunto vazio usando uma permissão que não possui. O que
     * ficou igual não se confere: não está sendo dado nem tirado.
     */
    public void alterarPermissoes(
            Membro autor, Membro alvo, Set<Permissao> novas, Instant agora) {
        autorizar(autor, alvo);
        Objects.requireNonNull(novas, "novas");

        Set<Permissao> atuais = alvo.getPermissoes();
        Set<Permissao> diferenca = EnumSet.noneOf(Permissao.class);
        for (Permissao permissao : Permissao.values()) {
            if (atuais.contains(permissao) != novas.contains(permissao)) {
                diferenca.add(permissao);
            }
        }

        autor.exigirQuePossui(diferenca);
        alvo.definirPermissoes(novas, agora);
    }

    // ── convite: o outro jeito de um vínculo nascer ─────────────────────────

    /**
     * Emite um convite. <b>É aqui que A2 é verificada pela primeira vez.</b>
     *
     * <p><b>O que esta operação não consegue checar</b>: se a pessoa daquele
     * telefone já tem vínculo nesta loja. O convite endereça um telefone, o
     * vínculo é de um {@code usuarioId}, e traduzir um no outro é dado do
     * {@code identity-service} — que a ADR-001 proíbe importar. A colisão é
     * detectada no aceite, que é onde o {@code usuarioId} finalmente aparece.
     * Não é falha de desenho: é a fronteira entre os dois serviços aparecendo
     * onde ela realmente está.
     */
    public Convite convidar(
            Membro autor, Telefone telefone, Set<Permissao> permissoesOferecidas, Instant agora) {
        exigirDaEquipe(autor, "autor");
        autor.exigirPodeGerenciarEquipeDe(estabelecimentoId);
        autor.exigirQuePossui(permissoesOferecidas);

        return Convite.novo(
                estabelecimentoId, telefone, permissoesOferecidas, autor.getId(), agora);
    }

    /** Só convite pendente se cancela — cancelar o que já foi aceito é remover. */
    public void cancelarConvite(Membro autor, Convite convite) {
        exigirDaEquipe(autor, "autor");
        exigirDaLoja(convite);
        autor.exigirPodeGerenciarEquipeDe(estabelecimentoId);

        if (convite.getEstado() != EstadoDoConvite.PENDENTE) {
            throw new ConviteInvalido();
        }
        convite.cancelar();
    }

    /**
     * O aceite. <b>É aqui que A2 é verificada pela segunda vez — e é a segunda
     * que se esquece.</b>
     *
     * <p>Entre o convite e o aceite podem passar dias. O convidante pode ter
     * perdido a permissão que estava concedendo, ter sido suspenso, ou ter
     * saído da loja. Validar só na emissão deixa um convite virar um privilégio
     * que ninguém mais tem autoridade para dar — e ele continuaria de pé,
     * assinado por um vínculo que já não existe.
     *
     * <p><b>Quem volta, volta {@code COLABORADOR}.</b> Se a pessoa já teve
     * vínculo e o perdeu, o aceite reativa o mesmo vínculo — o
     * {@code UNIQUE (usuario_id, estabelecimento_id)} não deixaria criar outro —
     * e o rebaixa. Sem isso haveria um caminho para restaurar um
     * {@code ADMINISTRADOR} removido sem nenhum administrador na jogada: bastaria
     * um gerente convidá-lo de volta. Manter o papel antigo pareceria gentileza
     * e seria escalada.
     *
     * <p>Devolve o vínculo, já dentro desta equipe. Quem chamou persiste os
     * dois: o vínculo e o convite, que acabou de virar {@code ACEITO}.
     */
    public Membro aceitar(Convite convite, String token, UUID usuarioId, Instant agora) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        exigirDaLoja(convite);
        convite.exigirUtilizavel(token, agora);

        Membro quemConvidou = porId(convite.getConvidadoPor())
                .orElseThrow(ConviteInvalido::new);
        quemConvidou.exigirPodeGerenciarEquipeDe(estabelecimentoId);
        quemConvidou.exigirQuePossui(convite.getPermissoesOferecidas());

        Membro existente = doUsuario(usuarioId).orElse(null);
        if (existente != null && existente.ativo()) {
            throw new JaPertenceAEquipe();
        }

        convite.marcarAceito(agora);

        if (existente != null) {
            existente.reativar(agora);
            existente.rebaixar(agora);
            existente.definirPermissoes(convite.getPermissoesOferecidas(), agora);
            return existente;
        }

        Membro novo = Membro.colaborador(
                usuarioId, estabelecimentoId, convite.getPermissoesOferecidas(), agora);
        membros.add(novo);
        return novo;
    }

    private void exigirDaLoja(Convite convite) {
        Objects.requireNonNull(convite, "convite");
        if (!convite.getEstabelecimentoId().equals(estabelecimentoId)) {
            throw new IllegalArgumentException("convite de outro estabelecimento");
        }
    }

    // ── A3: teorema nas seis, checagem na saída ─────────────────────────────

    /**
     * <b>A3 é teorema nas seis operações administrativas e checagem em uma
     * só</b> — e chegar a essa separação foi a descoberta desta rodada.
     *
     * <p>Comecei escrevendo a checagem em suspender, remover e rebaixar, e não
     * consegui construir um teste que a fizesse disparar em nenhuma das três.
     *
     * <p>A demonstração é curta. Para mexer num {@code ADMINISTRADOR}, A1 exige
     * que o autor seja um {@code ADMINISTRADOR} <b>ativo</b>. E ninguém
     * administra o próprio vínculo. Logo, sempre que o alvo é um administrador
     * ativo, existe um segundo administrador ativo — o autor. O alvo nunca é o
     * último, e a contagem nunca chega a zero:
     *
     * <pre>
     * alvo é ADMINISTRADOR ATIVO
     *   ⇒ autor é ADMINISTRADOR ATIVO   (A1)
     *   ∧ autor ≠ alvo                  (ninguém se administra)
     *   ⇒ administradoresAtivos ≥ 2
     * </pre>
     *
     * <p>Um {@code if} que nenhum teste consegue fazer disparar é a
     * peça-que-nunca-rodou em forma de guarda: dá a impressão de que a
     * invariante está sendo verificada e não verifica nada. Nas seis, o
     * {@code if} saiu e ficou o teorema, provado por teste.
     *
     * <p><b>Onde o teorema não alcança — e é por isso que esta contagem
     * existe.</b> A demonstração depende da premissa "ninguém administra o
     * próprio vínculo". {@link #sair} é exatamente a operação que a quebra: não
     * há autor separado do alvo, logo não há o segundo administrador garantido.
     * Lá A3 é <b>verificada</b>, com esta contagem, e é o único lugar do
     * sistema que a verifica.
     *
     * <p>O outro lugar que a quebrará é a automação da ADR-029, que cria ou
     * promove membro sem passar por A1 — e que ainda não existe.
     *
     * <p><b>E sob concorrência nada disso vale sem o cadeado.</b> Dois
     * administradores saindo ao mesmo tempo leem, cada um, uma equipe com dois
     * administradores ativos. Os dois passam por esta contagem, os dois saem, e
     * a loja fica órfã — <b>com a checagem escrita, correta, e executada nas
     * duas vezes</b>. É o modo de falha mais desagradável que existe: o código
     * está certo e o resultado está errado.
     *
     * <p>Com o {@code for update} na linha da loja, tomado pelo
     * {@code MembroRepositorio.equipeParaAlteracao} antes de montar esta lista,
     * a segunda transação só lê depois de a primeira ter gravado: ela conta um
     * administrador ativo — ela própria — e é recusada. O mesmo vale para o
     * teorema das outras seis, onde a segunda transação encontra o próprio
     * autor já {@code REMOVIDO} e A1 a recusa. <b>O cadeado não é
     * otimização</b>: é o que torna verdadeiras, fora do papel, tanto a
     * demonstração quanto a checagem.
     */
    public long administradoresAtivos() {
        return membros.stream().filter(Membro::ehAdministradorAtivo).count();
    }

    private void autorizar(Membro autor, Membro alvo) {
        exigirDaEquipe(autor, "autor");
        exigirDaEquipe(alvo, "alvo");
        autor.exigirAutoridadeSobre(alvo);
    }

    /**
     * Sem isto, alguém passaria um {@code Membro} carregado por fora e A3
     * contaria sobre uma lista que não o contém — a invariante ficaria correta
     * sobre a equipe errada.
     */
    private void exigirDaEquipe(Membro membro, String papelNaOperacao) {
        Objects.requireNonNull(membro, papelNaOperacao);
        boolean presente = membros.stream().anyMatch(atual -> atual.getId().equals(membro.getId()));
        if (!presente) {
            throw new IllegalArgumentException(
                    "%s não pertence a esta equipe: %s".formatted(papelNaOperacao, membro.getId()));
        }
    }

    // ── leitura ─────────────────────────────────────────────────────────────

    public UUID getEstabelecimentoId() {
        return estabelecimentoId;
    }

    public List<Membro> getMembros() {
        return List.copyOf(membros);
    }

    public Optional<Membro> porId(UUID membroId) {
        return membros.stream()
                .filter(membro -> membro.getId().equals(membroId))
                .findFirst();
    }

    public Optional<Membro> doUsuario(UUID usuarioId) {
        return membros.stream()
                .filter(membro -> membro.getUsuarioId().equals(usuarioId))
                .findFirst();
    }

    @Override
    public String toString() {
        return "Equipe{loja=%s, membros=%d, administradoresAtivos=%d}"
                .formatted(estabelecimentoId, membros.size(), administradoresAtivos());
    }
}
