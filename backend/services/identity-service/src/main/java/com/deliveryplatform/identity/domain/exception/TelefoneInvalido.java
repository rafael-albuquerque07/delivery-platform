package com.deliveryplatform.identity.domain.exception;

/**
 * O telefone recebido não é E.164 válido depois da normalização de
 * {@code Telefone.de(...)} — nem no formato original, nem com o prefixo
 * brasileiro presumido pela P2.
 */
public class TelefoneInvalido extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * <b>Sem o número na mensagem.</b> Ela acaba em stack trace, e stack trace
     * acaba em log — e o telefone é dado de identificação, como o
     * {@code Usuario.toString} já reconhece ao omiti-lo. Quem depura tem a
     * requisição; o log não precisa do número para dizer o que houve.
     */
    public TelefoneInvalido(String valorRecebido) {
        super("telefone inválido: %d caracteres recebidos"
                .formatted(valorRecebido == null ? 0 : valorRecebido.length()));
    }
}
