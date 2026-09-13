package com.deliveryplatform.merchant.domain.exception;

/**
 * O documento não tem 11 dígitos (CPF) nem 14 (CNPJ) depois de descartada a
 * formatação.
 *
 * <p><b>A mensagem não carrega o documento recebido</b>, e isso é deliberado. O
 * {@code CLAUDE.md} manda registrar log sem documento, e mensagem de exceção é
 * a forma mais comum de um dado vazar para o log sem ninguém ter decidido isso:
 * ela vai para o stack trace, para o {@code ProblemDetail} quando o tratador é
 * genérico, e para o arquivo de teste que alguém cola numa issue. O que o
 * chamador precisa saber para corrigir é <b>quantos</b> dígitos vieram — não
 * quais.
 *
 * <p>O {@code TelefoneInvalido} ao lado carrega o valor porque telefone não está
 * na lista do {@code CLAUDE.md} e porque o erro dele é quase sempre de formato.
 * A diferença entre os dois é decisão, não descuido.
 */
public class DocumentoInvalido extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DocumentoInvalido(int digitosRecebidos) {
        super("documento inválido: esperados 11 dígitos (CPF) ou 14 (CNPJ), vieram "
                + digitosRecebidos);
    }
}
