package com.deliveryplatform.identity.infrastructure.security;

/**
 * A chave não pôde ser carregada. Sempre fatal na subida: um
 * {@code identity-service} sem chave de assinatura é um serviço que aceita
 * requisição e não consegue emitir token nenhum.
 *
 * <p>A mensagem nunca inclui o conteúdo do arquivo — só o caminho.
 */
public class ChaveDeAssinaturaIndisponivel extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ChaveDeAssinaturaIndisponivel(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    public ChaveDeAssinaturaIndisponivel(String mensagem) {
        super(mensagem);
    }
}
