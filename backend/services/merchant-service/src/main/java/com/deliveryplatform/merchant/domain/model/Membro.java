package com.deliveryplatform.merchant.domain.model;

import com.deliveryplatform.merchant.domain.exception.PermissaoNaoPossuida;
import com.deliveryplatform.merchant.domain.exception.SemAutoridadeSobreMembro;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * O vínculo entre um usuário e um estabelecimento (`estabelecimento.md` §1 e
 * §2). Raiz de agregado própria, e não parte do {@link Estabelecimento}.
 *
 * <p><b>Por que raiz própria.</b> É o objeto mais lido do sistema — toda
 * requisição de todo serviço passa por ele. Dentro do agregado da loja, cada
 * checagem de permissão carregaria o estabelecimento inteiro, áreas e horários
 * inclusive. E o ciclo de vida é outro: um membro é convidado, aceita, é
 * suspenso, sem que a loja mude.
 *
 * <p><b>M1 é a forma desta classe, não uma checagem dentro dela.</b> Permissão
 * pertence ao par (usuário, estabelecimento) porque é aqui que ela mora, e não
 * no {@code Usuario} — que o {@code identity-service} guarda em outro banco, sem
 * saber que lojas existem. O mesmo usuário é administrador numa loja e
 * atendente noutra, e nada no `identity` precisa aprender isso.
 *
 * <p><b>Os mutadores são pacote-privados de propósito.</b> Toda alteração de
 * equipe passa pela {@link Equipe}, que é quem enxerga todos os membros e pode
 * verificar A3. Se {@code suspender} fosse público, existiria um caminho para
 * suspender o último administrador sem ninguém contar — e seria o caminho mais
 * curto, portanto o que alguém usaria. Aqui o compilador fecha a porta.
 */
public final class Membro {

    private final UUID id;
    private final UUID usuarioId;
    private final UUID estabelecimentoId;
    private final Instant criadoEm;

    private Papel papel;
    private Set<Permissao> permissoes;
    private EstadoDoMembro estado;
    private Instant alteradoEm;

    private Membro(
            UUID id,
            UUID usuarioId,
            UUID estabelecimentoId,
            Papel papel,
            Set<Permissao> permissoes,
            EstadoDoMembro estado,
            Instant criadoEm,
            Instant alteradoEm) {
        this.id = Objects.requireNonNull(id, "id");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.estabelecimentoId = Objects.requireNonNull(estabelecimentoId, "estabelecimentoId");
        this.papel = Objects.requireNonNull(papel, "papel");
        this.permissoes = copiar(permissoes);
        this.estado = Objects.requireNonNull(estado, "estado");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
        this.alteradoEm = Objects.requireNonNull(alteradoEm, "alteradoEm");
    }

    /**
     * O primeiro vínculo de uma loja: quem a cadastrou.
     *
     * <p><b>Nasce com todas as permissões, e não só com o papel.</b> O papel não
     * é lista de permissão nenhuma — quem só tem {@code ADMINISTRADOR} e o
     * conjunto vazio não consegue fazer nada, e a loja nasceria inoperante com
     * A3 satisfeita no papel. É a armadilha que a frase "papel não é uma lista
     * de permissões" cria quando lida pela metade.
     *
     * <p>Quem cria a loja e quando isso acontece é assunto da rodada que
     * escrever o cadastro do estabelecimento; esta fábrica existe para que a
     * loja nunca possa ser criada sem ele.
     */
    public static Membro fundador(UUID usuarioId, UUID estabelecimentoId, Instant agora) {
        return new Membro(
                UUID.randomUUID(),
                usuarioId,
                estabelecimentoId,
                Papel.ADMINISTRADOR,
                EnumSet.allOf(Permissao.class),
                EstadoDoMembro.ATIVO,
                agora,
                agora);
    }

    /**
     * O vínculo que o aceite de um convite cria. Sempre {@code COLABORADOR}:
     * convite nunca concede {@code ADMINISTRADOR}, porque promoção é ato
     * separado, feito por um administrador existente, sobre alguém que já
     * aceitou.
     */
    public static Membro colaborador(
            UUID usuarioId, UUID estabelecimentoId, Set<Permissao> permissoes, Instant agora) {
        return new Membro(
                UUID.randomUUID(),
                usuarioId,
                estabelecimentoId,
                Papel.COLABORADOR,
                permissoes,
                EstadoDoMembro.ATIVO,
                agora,
                agora);
    }

    /** Reconstrução do que já está persistido — usada pelo mapper de infraestrutura. */
    public static Membro reconstituir(
            UUID id,
            UUID usuarioId,
            UUID estabelecimentoId,
            Papel papel,
            Set<Permissao> permissoes,
            EstadoDoMembro estado,
            Instant criadoEm,
            Instant alteradoEm) {
        return new Membro(
                id, usuarioId, estabelecimentoId, papel, permissoes, estado, criadoEm, alteradoEm);
    }

    private static Set<Permissao> copiar(Set<Permissao> permissoes) {
        Objects.requireNonNull(permissoes, "permissoes");
        return permissoes.isEmpty() ? EnumSet.noneOf(Permissao.class) : EnumSet.copyOf(permissoes);
    }

    // ── a pergunta que todo serviço faz ─────────────────────────────────────

    /**
     * A resposta da autorização contextual, num lugar só.
     *
     * <p>{@code SUSPENSO} e {@code REMOVIDO} não perdem as permissões
     * guardadas — o estado é que decide. Zerar a lista ao suspender pareceria
     * mais seguro e destruiria a informação necessária para reativar alguém do
     * jeito que ele estava.
     */
    public boolean pode(Permissao permissao) {
        return ativo() && permissoes.contains(permissao);
    }

    public boolean ativo() {
        return estado == EstadoDoMembro.ATIVO;
    }

    public boolean ehAdministrador() {
        return papel == Papel.ADMINISTRADOR;
    }

    public boolean ehAdministradorAtivo() {
        return ehAdministrador() && ativo();
    }

    // ── A1: quem pode mexer em quem ─────────────────────────────────────────

    /**
     * A1 e M3, do lado de quem age.
     *
     * <p>Três condições, e a terceira é interpretação minha — o documento não a
     * escreve e eu registro isso aqui em vez de escondê-la no código:
     *
     * <ol>
     *   <li>O autor precisa estar <b>ativo</b>. Suspenso que ainda administra
     *       equipe faz da suspensão um enfeite.</li>
     *   <li>O autor precisa de {@link Permissao#GERENCIAR_EQUIPE}. Papel não
     *       concede nada sozinho — nem o de administrador.</li>
     *   <li>Se o alvo é {@code ADMINISTRADOR}, o autor também precisa ser.
     *       <b>O documento diz apenas que {@code GERENCIAR_EQUIPE} "nunca
     *       alcança ADMINISTRADOR"</b>, o que lido ao pé da letra tornaria um
     *       administrador impossível de remover por quem quer que seja — e
     *       administrador que saiu da empresa é o caso (c) da ADR-029, que
     *       existe justamente porque isso acontece. A leitura que preserva M3 e
     *       mantém a loja operável é: a permissão sozinha não alcança; o papel
     *       mais a permissão, sim. A emenda ao §2 vai junto com esta rodada.</li>
     * </ol>
     *
     * <p>E ninguém administra a si mesmo. Autopromoção é a escalada inteira em
     * um passo; as outras auto-operações são inócuas ou já barradas por A3 — e
     * distinguir uma da outra caso a caso é exatamente por onde a escalada
     * entra. Sair da própria loja é operação que o documento não desenha, e
     * fica de fora até que alguém a desenhe.
     */
    public void exigirAutoridadeSobre(Membro alvo) {
        Objects.requireNonNull(alvo, "alvo");

        if (id.equals(alvo.id)) {
            throw new SemAutoridadeSobreMembro("ninguém administra o próprio vínculo");
        }
        if (!estabelecimentoId.equals(alvo.estabelecimentoId)) {
            throw new SemAutoridadeSobreMembro("autor e alvo são de estabelecimentos diferentes");
        }
        if (!ativo()) {
            throw new SemAutoridadeSobreMembro("autor não tem vínculo ativo");
        }
        if (!permissoes.contains(Permissao.GERENCIAR_EQUIPE)) {
            throw new SemAutoridadeSobreMembro("autor não tem GERENCIAR_EQUIPE");
        }
        if (alvo.ehAdministrador() && !ehAdministrador()) {
            throw new SemAutoridadeSobreMembro(
                    "GERENCIAR_EQUIPE não alcança ADMINISTRADOR sem o papel (M3)");
        }
    }

    // ── A2: ninguém dá nem tira o que não tem ───────────────────────────────

    /**
     * A2, M4 e M5. Recebe a <b>diferença</b> entre o que o alvo tem e o que vai
     * passar a ter — os dois sentidos —, porque conceder e revogar têm o mesmo
     * limite.
     */
    public void exigirQuePossui(Set<Permissao> exigidas) {
        Set<Permissao> faltantes = EnumSet.noneOf(Permissao.class);
        for (Permissao permissao : exigidas) {
            if (!permissoes.contains(permissao)) {
                faltantes.add(permissao);
            }
        }
        if (!faltantes.isEmpty()) {
            throw new PermissaoNaoPossuida(faltantes);
        }
    }

    // ── mutadores: pacote-privados, entrada pela Equipe ─────────────────────

    void suspender(Instant agora) {
        this.estado = EstadoDoMembro.SUSPENSO;
        this.alteradoEm = Objects.requireNonNull(agora, "agora");
    }

    void reativar(Instant agora) {
        this.estado = EstadoDoMembro.ATIVO;
        this.alteradoEm = Objects.requireNonNull(agora, "agora");
    }

    void remover(Instant agora) {
        this.estado = EstadoDoMembro.REMOVIDO;
        this.alteradoEm = Objects.requireNonNull(agora, "agora");
    }

    void promover(Instant agora) {
        this.papel = Papel.ADMINISTRADOR;
        this.alteradoEm = Objects.requireNonNull(agora, "agora");
    }

    void rebaixar(Instant agora) {
        this.papel = Papel.COLABORADOR;
        this.alteradoEm = Objects.requireNonNull(agora, "agora");
    }

    void definirPermissoes(Set<Permissao> novas, Instant agora) {
        this.permissoes = copiar(novas);
        this.alteradoEm = Objects.requireNonNull(agora, "agora");
    }

    // ── leitura ─────────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getEstabelecimentoId() {
        return estabelecimentoId;
    }

    public Papel getPapel() {
        return papel;
    }

    /** Cópia: quem lê não altera o vínculo por fora dos mutadores. */
    public Set<Permissao> getPermissoes() {
        return EnumSet.copyOf(permissoes.isEmpty() ? EnumSet.noneOf(Permissao.class) : permissoes);
    }

    public EstadoDoMembro getEstado() {
        return estado;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAlteradoEm() {
        return alteradoEm;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof Membro membro && id.equals(membro.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Identificadores e estado, nada de pessoa. O nome e o telefone de quem está
     * do outro lado do {@code usuarioId} moram no {@code identity-service}, e é
     * bom que continuem lá: este {@code toString} acaba em log.
     */
    @Override
    public String toString() {
        return "Membro{id=%s, usuario=%s, loja=%s, papel=%s, estado=%s, permissoes=%d}"
                .formatted(id, usuarioId, estabelecimentoId, papel, estado, permissoes.size());
    }
}
