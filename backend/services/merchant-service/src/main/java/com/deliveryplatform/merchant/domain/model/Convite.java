package com.deliveryplatform.merchant.domain.model;

import com.deliveryplatform.merchant.domain.exception.ConviteInvalido;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * O convite para entrar na equipe de uma loja (`estabelecimento.md` §2). Raiz
 * de agregado própria: nasce antes do vínculo e morre quando ele nasce.
 *
 * <p><b>O token autoriza; o telefone endereça.</b> Quem aceita é quem apresenta
 * o token, e não quem prova ser dono daquele número. A diferença importa e a
 * razão é de fronteira: conferir o telefone exigiria perguntar ao
 * {@code identity-service} qual é o telefone de um {@code usuarioId}, e essa
 * porta não existe — a ADR-001 proíbe o {@code merchant} de importar o outro
 * serviço, e a chamada síncrona entre os dois é assunto da rodada C. O telefone
 * fica guardado porque é o endereço de entrega e o registro de para quem aquilo
 * foi mandado; no dia em que a porta existir, conferir os dois é uma linha.
 *
 * <p><b>O token nasce aqui dentro, e não vem de uma porta.</b> É a diferença
 * para o {@code CodigoDeVerificacao} do {@code identity}, que recebe o código
 * de um {@link com.deliveryplatform.merchant.domain.model.Convite gerador}
 * injetado: lá o teste precisa <i>prever</i> o valor para conferi-lo; aqui o
 * teste lê o token do próprio convite, exatamente como o operador lê da tabela.
 * Sem necessidade de prever, a porta seria uma peça a mais para garantir uma
 * coisa que o {@link SecureRandom} já garante — e uma chance a mais de alguém
 * ligar um {@code Random} comum do outro lado.
 *
 * <p><b>Guardado em claro</b>, pelo mesmo argumento da ADR-042 §2: enquanto o
 * transporte for humano, a tabela <i>é</i> o canal de entrega, e um token com
 * hash não pode ser lido por quem precisa entregá-lo. A mesma condição de
 * término vale — no dia em que o {@code CanalPort} existir, o token vira hash
 * na mesma rodada.
 *
 * <p><b>Convite nunca concede {@code ADMINISTRADOR}</b>, e isso não é checagem:
 * o aceite chama {@link Membro#colaborador}, e não há outro caminho. Promoção
 * é ato separado, feito por um administrador existente, sobre alguém que já
 * aceitou.
 */
public final class Convite {

    /**
     * Sete dias. O documento diz que <i>"entre o convite e o aceite podem passar
     * dias"</i>, então horas não servem; e convite que vive um mês é chave
     * parada no bolso de alguém que já mudou de ideia. É proposta de partida,
     * como a janela de 72 h da ADR-029, e muda com o primeiro comerciante real.
     */
    public static final Duration VALIDADE = Duration.ofDays(7);

    private static final int BYTES_DO_TOKEN = 32;
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private final UUID id;
    private final UUID estabelecimentoId;
    private final Telefone telefone;
    private final String token;
    private final Set<Permissao> permissoesOferecidas;
    private final UUID convidadoPor;
    private final Instant criadoEm;
    private final Instant expiraEm;

    private EstadoDoConvite estado;
    private Instant aceitoEm;

    private Convite(
            UUID id,
            UUID estabelecimentoId,
            Telefone telefone,
            String token,
            Set<Permissao> permissoesOferecidas,
            UUID convidadoPor,
            EstadoDoConvite estado,
            Instant criadoEm,
            Instant expiraEm,
            Instant aceitoEm) {
        this.id = Objects.requireNonNull(id, "id");
        this.estabelecimentoId = Objects.requireNonNull(estabelecimentoId, "estabelecimentoId");
        this.telefone = Objects.requireNonNull(telefone, "telefone");
        this.token = exigirToken(token);
        this.permissoesOferecidas = copiar(permissoesOferecidas);
        this.convidadoPor = Objects.requireNonNull(convidadoPor, "convidadoPor");
        this.estado = Objects.requireNonNull(estado, "estado");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
        this.expiraEm = Objects.requireNonNull(expiraEm, "expiraEm");

        if (!expiraEm.isAfter(criadoEm)) {
            throw new IllegalArgumentException("expiraEm precisa vir depois de criadoEm");
        }
        if ((estado == EstadoDoConvite.ACEITO) != (aceitoEm != null)) {
            throw new IllegalArgumentException(
                    "aceitoEm existe se, e somente se, o convite está ACEITO");
        }
        this.aceitoEm = aceitoEm;
    }

    /**
     * {@code convidadoPor} é o <b>id do vínculo</b>, não o do usuário — e é uma
     * divergência consciente da palavra que o documento usa.
     *
     * <p>M1 diz que permissão pertence ao vínculo, nunca ao usuário. Quem
     * concede permissão num convite concede pelo vínculo que tem <i>naquela
     * loja</i>, e é esse vínculo que A2 vai reconferir no aceite. Apontar para
     * o usuário obrigaria a procurar de novo qual dos vínculos dele era o
     * relevante — e daria margem a achar o errado.
     */
    public static Convite novo(
            UUID estabelecimentoId,
            Telefone telefone,
            Set<Permissao> permissoesOferecidas,
            UUID convidadoPor,
            Instant agora) {
        Objects.requireNonNull(agora, "agora");
        return new Convite(
                UUID.randomUUID(),
                estabelecimentoId,
                telefone,
                gerarToken(),
                permissoesOferecidas,
                convidadoPor,
                EstadoDoConvite.PENDENTE,
                agora,
                agora.plus(VALIDADE),
                null);
    }

    /** Reconstrução do que já está persistido — usada pelo mapper. */
    public static Convite reconstituir(
            UUID id,
            UUID estabelecimentoId,
            Telefone telefone,
            String token,
            Set<Permissao> permissoesOferecidas,
            UUID convidadoPor,
            EstadoDoConvite estado,
            Instant criadoEm,
            Instant expiraEm,
            Instant aceitoEm) {
        return new Convite(id, estabelecimentoId, telefone, token, permissoesOferecidas,
                convidadoPor, estado, criadoEm, expiraEm, aceitoEm);
    }

    private static String gerarToken() {
        byte[] bytes = new byte[BYTES_DO_TOKEN];
        ALEATORIO.nextBytes(bytes);
        return BASE64.encodeToString(bytes);
    }

    private static String exigirToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token é obrigatório");
        }
        return token;
    }

    private static Set<Permissao> copiar(Set<Permissao> permissoes) {
        Objects.requireNonNull(permissoes, "permissoesOferecidas");
        return permissoes.isEmpty() ? EnumSet.noneOf(Permissao.class) : EnumSet.copyOf(permissoes);
    }

    // ── o que o aceite pergunta ─────────────────────────────────────────────

    /**
     * Quatro motivos, uma recusa — e a comparação do token é em tempo
     * constante.
     *
     * <p>Com 32 bytes de {@link SecureRandom} o ganho de um atacante por
     * temporização é teórico. Escrever {@code equals} aqui, porém, é ensinar o
     * padrão errado no lugar onde ele mais importa: este token é a única coisa
     * entre um estranho e o acesso a uma loja.
     */
    public void exigirUtilizavel(String tokenApresentado, Instant agora) {
        Objects.requireNonNull(agora, "agora");

        if (estado != EstadoDoConvite.PENDENTE || expirado(agora) || !confere(tokenApresentado)) {
            throw new ConviteInvalido();
        }
    }

    private boolean confere(String tokenApresentado) {
        if (tokenApresentado == null) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                tokenApresentado.getBytes(StandardCharsets.UTF_8));
    }

    /** Derivado, nunca guardado — ver {@link EstadoDoConvite}. */
    public boolean expirado(Instant agora) {
        return !agora.isBefore(expiraEm);
    }

    public boolean pendente(Instant agora) {
        return estado == EstadoDoConvite.PENDENTE && !expirado(agora);
    }

    // ── mutadores: pacote-privados, entrada pela Equipe ─────────────────────

    void marcarAceito(Instant agora) {
        this.estado = EstadoDoConvite.ACEITO;
        this.aceitoEm = Objects.requireNonNull(agora, "agora");
    }

    void cancelar() {
        this.estado = EstadoDoConvite.CANCELADO;
    }

    // ── leitura ─────────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getEstabelecimentoId() {
        return estabelecimentoId;
    }

    public Telefone getTelefone() {
        return telefone;
    }

    /** O valor a ser entregue à pessoa convidada, hoje pelas mãos do operador. */
    public String getToken() {
        return token;
    }

    public Set<Permissao> getPermissoesOferecidas() {
        return EnumSet.copyOf(
                permissoesOferecidas.isEmpty()
                        ? EnumSet.noneOf(Permissao.class)
                        : permissoesOferecidas);
    }

    public UUID getConvidadoPor() {
        return convidadoPor;
    }

    public EstadoDoConvite getEstado() {
        return estado;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getAceitoEm() {
        return aceitoEm;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof Convite convite && id.equals(convite.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Sem token, sem telefone e sem a lista de permissões. O primeiro é
     * credencial; o segundo é dado pessoal de alguém que ainda nem aceitou; a
     * terceira desenha o cargo da pessoa no log de quem quiser ler.
     */
    @Override
    public String toString() {
        return "Convite{id=%s, loja=%s, estado=%s, expiraEm=%s, permissoes=%d}"
                .formatted(id, estabelecimentoId, estado, expiraEm, permissoesOferecidas.size());
    }
}
