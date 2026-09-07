package com.deliveryplatform.identity.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * Gera um PKCS#8 PEM de verdade em disco.
 *
 * <p><b>Por que não um par efêmero em memória.</b> A ADR-037 registrava como
 * consequência negativa que o carregamento de PEM — o caminho que roda em
 * produção — não seria exercitado por teste nenhum, e é justamente o que quebra
 * na primeira implantação. Gerando o arquivo, o caminho de produção passa a ser
 * o caminho de teste, e a consequência negativa deixa de existir.
 *
 * <p>Nenhum material de chave entra no repositório: o arquivo nasce em
 * diretório temporário e é removido ao fim da JVM.
 */
public final class GeradorDeChaveDeTeste {

    private static Path unica;

    private GeradorDeChaveDeTeste() {
    }

    /**
     * O caminho de uma chave gerada uma vez por execução da JVM.
     *
     * <p>Existe porque <b>todo</b> {@code @SpringBootTest} deste serviço passa a
     * precisar de chave: a {@code ChaveDeAssinatura} sobe com o contexto, e sem
     * ela nenhum contexto sobe — inclusive o de testes que não têm nada com
     * token. Gerar uma chave RSA de 2048 bits por classe de teste é desperdício
     * de segundos, e o que se prova aqui não depende de a chave ser diferente.
     */
    public static synchronized String caminhoDaChaveUnica() {
        if (unica == null) {
            try {
                Path diretorio = Files.createTempDirectory("delivery-jwt");
                diretorio.toFile().deleteOnExit();
                unica = escreverPem(diretorio.resolve("jwt-private.pem"));
                unica.toFile().deleteOnExit();
            } catch (Exception e) {
                throw new IllegalStateException("não foi possível preparar a chave de teste", e);
            }
        }
        return unica.toString();
    }

    public static Path escreverPem(Path destino) {
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(2048);
            KeyPair par = gerador.generateKeyPair();

            String base64 = Base64.getMimeEncoder(64, "\n".getBytes())
                    .encodeToString(par.getPrivate().getEncoded());

            Files.writeString(destino,
                    "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n");

            return destino;
        } catch (Exception e) {
            throw new IllegalStateException("não foi possível gerar a chave de teste", e);
        }
    }
}
