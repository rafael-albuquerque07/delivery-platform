package com.deliveryplatform.catalog.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Um {@code merchant-service} de mentira, com os quatro comportamentos que a
 * autorização precisa distinguir.
 *
 * <p>Mesmo desenho do {@code UpstreamsDeMentira} da E-A e do
 * {@code IdentityDeMentira} da C-A: um {@link HttpServer} do próprio JDK,
 * nenhuma dependência nova, e ele morre com o teste. <b>Um dublê de
 * {@code RestClient} provaria que uma classe foi chamada; isto prova que uma
 * requisição HTTP saiu, com o cabeçalho certo, e que o tempo limite é de
 * verdade.</b>
 *
 * <h2>Os quatro modos, e por que o quarto não é luxo</h2>
 *
 * <ul>
 *   <li>{@link Modo#RESPONDE} — 200 com o corpo programado;</li>
 *   <li>{@link Modo#NEGA} — 403, que é <i>uma resposta</i>;</li>
 *   <li>{@link Modo#TOKEN_RECUSADO} — 401, que <b>não</b> é: significa que os
 *       dois serviços discordam sobre o que é um token válido;</li>
 *   <li>{@link Modo#ERRO} — 500, que <i>não</i> é;</li>
 *   <li>{@link Modo#SILENCIO} — não responde, e o cliente estoura por tempo.</li>
 * </ul>
 *
 * <p>O silêncio é o único jeito de provar que o tempo limite existe. Um serviço
 * sem tempo limite passa em todo teste de 500 e derruba o catálogo no dia em
 * que o {@code merchant} ficar lento em vez de cair — que é o modo de falha mais
 * comum e o menos testado.
 *
 * <p><b>O executor com várias linhas de execução é obrigatório por causa dele.</b>
 * O {@code HttpServer} do JDK usa, por omissão, um executor que atende uma
 * requisição por vez: um tratador preso no modo silêncio travaria todas as
 * requisições seguintes, e o teste seguinte falharia por um motivo que não é o
 * dele.
 *
 * <h2>Ele guarda o que recebeu</h2>
 *
 * <p>{@link #autorizacoesRecebidas()} é o que prova o encaminhamento do token:
 * não basta o catálogo responder certo, tem de ser <b>o token do portador</b>
 * que chegou aqui. Sem essa lista, um adaptador que mandasse um token qualquer
 * — ou nenhum — passaria em todos os outros testes.
 */
public final class MerchantDeMentira implements AutoCloseable {

    public enum Modo { RESPONDE, NEGA, TOKEN_RECUSADO, ERRO, SILENCIO }

    /**
     * O sexto modo, e ele é de outra rota: o expediente corrente
     * ({@code /internal/…/expediente-corrente}, G-C1). Fica separado dos cinco
     * da autorização porque a marcação chama as duas rotas na mesma requisição,
     * e cada uma precisa responder o que o caso pede.
     */
    public enum ModoDoExpediente { DATA, SEM_HORARIO, ERRO }

    private final HttpServer servidor;
    private final List<String> autorizacoes = new CopyOnWriteArrayList<>();
    private final List<String> caminhos = new CopyOnWriteArrayList<>();

    private volatile Modo modo = Modo.RESPONDE;
    private volatile String corpo = "{}";
    private volatile Duration silencio = Duration.ofSeconds(10);
    private volatile ModoDoExpediente modoDoExpediente = ModoDoExpediente.DATA;
    private volatile LocalDate expediente = LocalDate.parse("2026-10-01");

    private MerchantDeMentira(HttpServer servidor) {
        this.servidor = servidor;
    }

    public static MerchantDeMentira subir() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.setExecutor(Executors.newCachedThreadPool());

            MerchantDeMentira merchant = new MerchantDeMentira(servidor);
            servidor.createContext("/", merchant::atender);
            servidor.start();
            return merchant;
        } catch (IOException e) {
            throw new IllegalStateException("não subiu o merchant de mentira", e);
        }
    }

    private void atender(com.sun.net.httpserver.HttpExchange troca) throws IOException {
        autorizacoes.add(troca.getRequestHeaders().getFirst("Authorization"));
        caminhos.add(troca.getRequestURI().getPath());

        String caminho = troca.getRequestURI().getPath();
        if (caminho.endsWith("/expediente-corrente")) {
            atenderExpediente(troca, caminho);
            return;
        }

        switch (modo) {
            case SILENCIO -> {
                try {
                    Thread.sleep(silencio);
                } catch (InterruptedException interrompido) {
                    Thread.currentThread().interrupt();
                }
                troca.close();
            }
            case TOKEN_RECUSADO -> responder(troca, 401, """
                    {"type":"about:blank","title":"Unauthorized","status":401}""");
            case NEGA -> responder(troca, 403, """
                    {"type":"about:blank","title":"Forbidden","status":403,\
                    "detail":"sem acesso a este estabelecimento"}""");
            case ERRO -> responder(troca, 500, """
                    {"type":"about:blank","title":"Internal Server Error","status":500}""");
            case RESPONDE -> responder(troca, 200, corpo);
        }
    }

    /** O corpo ecoa a loja do caminho — o adaptador confere, e é bom que confira. */
    private void atenderExpediente(com.sun.net.httpserver.HttpExchange troca, String caminho)
            throws IOException {
        String[] partes = caminho.split("/");
        String loja = partes[partes.length - 2];
        switch (modoDoExpediente) {
            case DATA -> responder(troca, 200, """
                    {"estabelecimentoId":"%s","expedienteDeReferencia":"%s"}"""
                    .formatted(loja, expediente));
            case SEM_HORARIO -> responder(troca, 409, """
                    {"type":"about:blank","title":"Conflict","status":409,\
                    "detail":"a loja não abre por horário"}""");
            case ERRO -> responder(troca, 500, """
                    {"type":"about:blank","title":"Internal Server Error","status":500}""");
        }
    }

    private static void responder(com.sun.net.httpserver.HttpExchange troca, int status, String json)
            throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        troca.sendResponseHeaders(status, bytes.length);
        try (var saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }

    // ── o que o teste programa ──────────────────────────────────────────────

    /** 200 com um contexto de acesso. As permissões vão como texto, de propósito. */
    public MerchantDeMentira respondeCom(UUID usuarioId, UUID estabelecimentoId,
                                         String papel, List<String> permissoes) {
        String lista = permissoes.stream()
                .map(permissao -> "\"" + permissao + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        this.corpo = """
                {"usuarioId":"%s","estabelecimentoId":"%s","papel":"%s","permissoes":[%s]}"""
                .formatted(usuarioId, estabelecimentoId, papel, lista);
        this.modo = Modo.RESPONDE;
        return this;
    }

    public MerchantDeMentira nega() {
        this.modo = Modo.NEGA;
        return this;
    }

    /** 401: o merchant recusou o token que o catalog aceitou. */
    public MerchantDeMentira recusaOToken() {
        this.modo = Modo.TOKEN_RECUSADO;
        return this;
    }

    public MerchantDeMentira estoura() {
        this.modo = Modo.ERRO;
        return this;
    }

    public MerchantDeMentira emSilencio() {
        this.modo = Modo.SILENCIO;
        return this;
    }

    /** Corpo cru, para os casos que não cabem no {@link #respondeCom}. */
    public MerchantDeMentira respondeCruamente(String json) {
        this.corpo = json;
        this.modo = Modo.RESPONDE;
        return this;
    }

    /** O expediente corrente responde esta data — o padrão é 01/10/2026. */
    public MerchantDeMentira expedienteE(LocalDate dia) {
        this.expediente = dia;
        this.modoDoExpediente = ModoDoExpediente.DATA;
        return this;
    }

    /** O expediente corrente responde 409: a loja não abre por horário. */
    public MerchantDeMentira semHorario() {
        this.modoDoExpediente = ModoDoExpediente.SEM_HORARIO;
        return this;
    }

    /** O expediente corrente responde 500. A autorização segue no modo dela. */
    public MerchantDeMentira expedienteEstoura() {
        this.modoDoExpediente = ModoDoExpediente.ERRO;
        return this;
    }

    // ── o que o teste confere ───────────────────────────────────────────────

    public List<String> autorizacoesRecebidas() {
        return List.copyOf(autorizacoes);
    }

    public List<String> caminhosRecebidos() {
        return List.copyOf(caminhos);
    }

    public void esquecerOQueRecebeu() {
        autorizacoes.clear();
        caminhos.clear();
    }

    public String uri() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
