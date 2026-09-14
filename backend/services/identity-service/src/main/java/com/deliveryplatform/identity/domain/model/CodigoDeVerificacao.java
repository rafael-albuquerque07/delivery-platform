package com.deliveryplatform.identity.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A prova de que quem está se cadastrando atende aquele telefone (ADR-042).
 *
 * <p>Raiz de agregado própria, e não um campo do {@code Usuario}, por um motivo
 * de ordem: ele existe <b>antes</b> do usuário e morre quando o usuário nasce.
 * Enfiá-lo no {@code Usuario} exigiria um {@code Usuario} sem telefone
 * verificado para hospedá-lo — exatamente o estado que a U4 proíbe.
 *
 * <p><b>O código é guardado em texto claro</b>, e a ADR-042 §2 é o lugar onde
 * isso está argumentado: enquanto o transporte for humano, a tabela <i>é</i> o
 * canal de entrega, e um código com hash não pode ser lido por quem precisa
 * entregá-lo. Vive dez minutos, serve uma vez, e o que ele abre é o cadastro de
 * um telefone que ainda não tem conta.
 *
 * <p><b>Três limites, e nenhum é configurável.</b> Validade, tentativas e a
 * quantidade de dígitos são constantes de domínio, pelo mesmo argumento com que
 * a ADR-025 recusou tornar a {@code HORA_DE_CORTE} um campo: cada um deles é
 * mais uma decisão para alguém entender e um valor que, quando mexido, muda o
 * significado do que já aconteceu.
 */
public final class CodigoDeVerificacao {

    /** Curto o bastante para o risco do texto claro, longo o bastante para alguém digitar. */
    public static final Duration VALIDADE = Duration.ofMinutes(10);

    public static final int DIGITOS = 6;

    /**
     * Sem este limite, seis dígitos se percorrem inteiros em minutos e a
     * verificação não verifica nada — a ADR-042 §1 seria falsa. É invariante
     * deste objeto, e não a política de limite por telefone e por janela, que
     * continua aberta em {@code usuario.md} §7.
     */
    public static final int TENTATIVAS_MAXIMAS = 5;

    private static final Pattern SO_DIGITOS = Pattern.compile("^\\d{" + DIGITOS + "}$");

    private final UUID id;
    private final Telefone telefone;
    private final String codigo;
    private final Instant criadoEm;
    private final Instant expiraEm;
    private int tentativas;

    private CodigoDeVerificacao(
            UUID id,
            Telefone telefone,
            String codigo,
            Instant criadoEm,
            Instant expiraEm,
            int tentativas) {
        this.id = Objects.requireNonNull(id, "id");
        this.telefone = Objects.requireNonNull(telefone, "telefone");
        this.codigo = validarCodigo(codigo);
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
        this.expiraEm = Objects.requireNonNull(expiraEm, "expiraEm");

        if (!expiraEm.isAfter(criadoEm)) {
            throw new IllegalArgumentException("expiraEm precisa vir depois de criadoEm");
        }
        if (tentativas < 0) {
            throw new IllegalArgumentException("tentativas não pode ser negativo");
        }
        this.tentativas = tentativas;
    }

    /** A validade é contada a partir de {@code agora}, não do relógio da JVM. */
    public static CodigoDeVerificacao novo(Telefone telefone, String codigo, Instant agora) {
        Objects.requireNonNull(agora, "agora");
        return new CodigoDeVerificacao(
                UUID.randomUUID(), telefone, codigo, agora, agora.plus(VALIDADE), 0);
    }

    /** Reconstrução do que já está persistido — usada pelo mapper de infraestrutura. */
    public static CodigoDeVerificacao reconstituir(
            UUID id,
            Telefone telefone,
            String codigo,
            Instant criadoEm,
            Instant expiraEm,
            int tentativas) {
        return new CodigoDeVerificacao(id, telefone, codigo, criadoEm, expiraEm, tentativas);
    }

    private static String validarCodigo(String codigo) {
        if (codigo == null || !SO_DIGITOS.matcher(codigo).matches()) {
            throw new IllegalArgumentException(
                    "código precisa ter exatamente %d dígitos".formatted(DIGITOS));
        }
        return codigo;
    }

    /**
     * Confere e <b>gasta uma tentativa</b>, na mesma chamada.
     *
     * <p>Separar em {@code registrarTentativa()} e {@code confere()} deixaria
     * quem chama livre para conferir sem gastar — e é exatamente o que um laço
     * de força bruta faria. Quem consome precisa persistir o objeto depois de
     * uma tentativa falha, ou o contador não sai da memória.
     *
     * <p>Código expirado ou estourado devolve falso <b>sem comparar</b>, e sem
     * gastar tentativa: já não há o que gastar.
     */
    public boolean confere(String informado, Instant agora) {
        if (!valido(agora)) {
            return false;
        }

        tentativas++;

        if (informado == null) {
            return false;
        }

        // Comparação em tempo constante. Com seis dígitos o ganho de um
        // atacante seria pequeno, mas escrever equals() aqui é ensinar o
        // padrão errado para o dia em que a comparação for de um token.
        return MessageDigest.isEqual(
                codigo.getBytes(StandardCharsets.UTF_8),
                informado.getBytes(StandardCharsets.UTF_8));
    }

    public boolean valido(Instant agora) {
        Objects.requireNonNull(agora, "agora");
        return agora.isBefore(expiraEm) && tentativas < TENTATIVAS_MAXIMAS;
    }

    public boolean expirado(Instant agora) {
        return !agora.isBefore(expiraEm);
    }

    public UUID getId() {
        return id;
    }

    public Telefone getTelefone() {
        return telefone;
    }

    /** O valor a ser entregue ao comerciante pelo operador (ADR-042 §1). */
    public String getCodigo() {
        return codigo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public int getTentativas() {
        return tentativas;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof CodigoDeVerificacao codigo && id.equals(codigo.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Sem o código e sem o telefone. O primeiro é credencial de uso único; o
     * segundo é dado pessoal — e {@code toString} é como um campo chega ao log
     * sem ninguém decidir isso.
     */
    @Override
    public String toString() {
        return "CodigoDeVerificacao{id=%s, expiraEm=%s, tentativas=%d}"
                .formatted(id, expiraEm, tentativas);
    }
}
