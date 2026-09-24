package com.deliveryplatform.gateway.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Os oito serviços do outro lado do gateway, de mentira.
 *
 * <p>Cada um é um {@link HttpServer} do próprio JDK numa porta própria, e todos
 * respondem a mesma coisa: <b>o próprio nome, o caminho que receberam, e se
 * veio cabeçalho {@code Authorization}</b>. É o suficiente para o teste afirmar
 * três coisas que nenhum outro meio prova:
 *
 * <ol>
 *   <li><b>a rota casou</b> — chegou alguma coisa;</li>
 *   <li><b>casou com o serviço certo</b> — o nome que voltou é o esperado, e
 *       portas diferentes não se confundem;</li>
 *   <li><b>o caminho atravessou inteiro</b> — o gateway não reescreveu nem
 *       cortou prefixo.</li>
 * </ol>
 *
 * <p><b>Por que oito servidores e não um dublê de {@code RestClient}.</b> Pelo
 * mesmo motivo que fez o {@code IdentityDeMentira} da C-A servir o JWKS num HTTP
 * de verdade: um dublê provaria que uma classe de roteamento foi chamada, e não
 * provaria que o namespace do {@code application.yml} foi lido. E esse é
 * exatamente o ponto — o próprio arquivo avisava, em quinze linhas de
 * comentário, que o prefixo nunca tinha sido verificado e que configuração
 * ignorada é silenciosa: o gateway sobe e não roteia.
 */
public final class UpstreamsDeMentira implements AutoCloseable {

    public static final String IDENTITY = "identity";
    public static final String MERCHANT = "merchant";
    public static final String CATALOG = "catalog";
    public static final String SETTLEMENT = "settlement";
    public static final String ORDER = "order";
    public static final String PAYMENT = "payment";
    public static final String DELIVERY = "delivery";
    public static final String CONVERSATION = "conversation";

    private static final String[] TODOS = {
            IDENTITY, MERCHANT, CATALOG, SETTLEMENT, ORDER, PAYMENT, DELIVERY, CONVERSATION
    };

    private final Map<String, HttpServer> servidores = new LinkedHashMap<>();

    private UpstreamsDeMentira() {
    }

    public static UpstreamsDeMentira subir() {
        UpstreamsDeMentira upstreams = new UpstreamsDeMentira();
        try {
            for (String nome : TODOS) {
                HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                servidor.createContext("/", troca -> responder(troca, nome));
                servidor.start();
                upstreams.servidores.put(nome, servidor);
            }
            return upstreams;
        } catch (IOException e) {
            upstreams.close();
            throw new IllegalStateException("não subiram os upstreams de mentira", e);
        }
    }

    private static void responder(com.sun.net.httpserver.HttpExchange troca, String nome)
            throws IOException {
        boolean temToken = troca.getRequestHeaders().containsKey("Authorization");
        String corpo = """
                {"servico":"%s","caminho":"%s","metodo":"%s","autorizacao":%s}"""
                .formatted(nome, troca.getRequestURI().getPath(), troca.getRequestMethod(), temToken);

        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        troca.sendResponseHeaders(200, bytes.length);
        try (var saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }

    public String uriDe(String servico) {
        HttpServer servidor = servidores.get(servico);
        if (servidor == null) {
            throw new IllegalArgumentException("upstream desconhecido: " + servico);
        }
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @Override
    public void close() {
        servidores.values().forEach(servidor -> servidor.stop(0));
        servidores.clear();
    }
}
