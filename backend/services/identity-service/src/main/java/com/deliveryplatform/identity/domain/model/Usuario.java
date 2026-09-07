package com.deliveryplatform.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A conta de quem entra no painel (docs/dominio/usuario.md). Raiz de
 * agregado — não guarda permissão, papel nem estabelecimento (U6, U8): isso
 * vive no {@code Vinculo}, no merchant-service.
 *
 * <p>Não há caminho que crie um {@code Usuario} com telefone não verificado
 * (U4): o telefone é o canal de cadastro (ADR-036) e nasce verificado
 * (ADR-029 §1). {@link #novo} e {@link #reconstituir} exigem
 * {@code telefoneVerificadoEm} não nulo.
 */
public final class Usuario {

    private final UUID id;
    private String nome;
    private Telefone telefone;
    private Instant telefoneVerificadoEm;
    private Email email;
    private Instant emailVerificadoEm;
    private final String hashDaSenha;

    private Usuario(
            UUID id,
            String nome,
            Telefone telefone,
            Instant telefoneVerificadoEm,
            Email email,
            Instant emailVerificadoEm,
            String hashDaSenha) {
        this.id = Objects.requireNonNull(id, "id");
        this.nome = validarNome(nome);
        this.telefone = Objects.requireNonNull(telefone, "telefone");
        this.telefoneVerificadoEm = Objects.requireNonNull(
                telefoneVerificadoEm,
                "telefone nasce verificado no cadastro — não existe Usuario com telefone não verificado (U4)");
        this.email = email;
        this.emailVerificadoEm = emailVerificadoEm;
        this.hashDaSenha = Objects.requireNonNull(hashDaSenha, "hashDaSenha");
    }

    /** Cadastro: só telefone e senha são obrigatórios. E-mail entra depois, se entrar. */
    public static Usuario novo(String nome, Telefone telefone, Instant telefoneVerificadoEm, String hashDaSenha) {
        return new Usuario(UUID.randomUUID(), nome, telefone, telefoneVerificadoEm, null, null, hashDaSenha);
    }

    /** Reconstrução a partir do que já está persistido — usada pelo mapper de infraestrutura. */
    public static Usuario reconstituir(
            UUID id,
            String nome,
            Telefone telefone,
            Instant telefoneVerificadoEm,
            Email email,
            Instant emailVerificadoEm,
            String hashDaSenha) {
        return new Usuario(id, nome, telefone, telefoneVerificadoEm, email, emailVerificadoEm, hashDaSenha);
    }

    private static String validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome é obrigatório e não pode ser vazio");
        }
        return nome.trim();
    }

    /** Cadastra ou substitui o segundo canal. Novo e-mail nasce não verificado. */
    public void adicionarEmail(Email email) {
        this.email = Objects.requireNonNull(email, "email");
        this.emailVerificadoEm = null;
    }

    public void verificarEmail(Instant quando) {
        if (this.email == null) {
            throw new IllegalStateException("não há e-mail cadastrado para verificar");
        }
        this.emailVerificadoEm = Objects.requireNonNull(quando, "quando");
    }

    public boolean telefoneVerificado() {
        return telefoneVerificadoEm != null;
    }

    public boolean emailVerificado() {
        return emailVerificadoEm != null;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Telefone getTelefone() {
        return telefone;
    }

    public Instant getTelefoneVerificadoEm() {
        return telefoneVerificadoEm;
    }

    public Email getEmail() {
        return email;
    }

    public Instant getEmailVerificadoEm() {
        return emailVerificadoEm;
    }

    public String getHashDaSenha() {
        return hashDaSenha;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof Usuario usuario && id.equals(usuario.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Nunca hash, e-mail nem nome aqui — os três são dado pessoal ou
     * credencial, e {@code toString} acaba em log sem ninguém decidir isso.
     */
    @Override
    public String toString() {
        return "Usuario{id=%s, telefoneVerificado=%s, emailVerificado=%s}"
                .formatted(id, telefoneVerificado(), emailVerificado());
    }
}
