package com.deliveryplatform.gateway.support;

import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * O gateway de pé, com os oito serviços e o emissor de tokens ao redor.
 *
 * <p>Base das duas suítes da rodada E-A. Ela existe porque <b>este módulo nunca
 * tinha sido subido</b>: não havia sequer um contexto Spring para pendurar um
 * teste, e o {@code src/test} inteiro era um {@code .gitkeep}.
 *
 * <p>Os dublês são estáticos e sobem uma vez para a execução inteira — são
 * servidores HTTP em processo, não contêineres, e o custo é de milissegundos.
 */
public abstract class GatewayNoAr {

    protected static final UpstreamsDeMentira UPSTREAMS = UpstreamsDeMentira.subir();
    protected static final IdentityDeMentira IDENTITY = IdentityDeMentira.subir();

    static {
        // Derrubar num @AfterAll mataria os dublês ao fim da PRIMEIRA classe de
        // teste, e a segunda encontraria portas fechadas. Eles vivem enquanto a
        // JVM viver — mesmo raciocínio dos contêineres compartilhados do
        // merchant, com um custo muito menor.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            UPSTREAMS.close();
            IDENTITY.close();
        }));
    }

    @DynamicPropertySource
    static void apontarParaOsDubles(DynamicPropertyRegistry registro) {
        registro.add("SERVICE_IDENTITY_URI", () -> UPSTREAMS.uriDe(UpstreamsDeMentira.IDENTITY));
        registro.add("SERVICE_MERCHANT_URI", () -> UPSTREAMS.uriDe(UpstreamsDeMentira.MERCHANT));
        registro.add("SERVICE_CATALOG_URI", () -> UPSTREAMS.uriDe(UpstreamsDeMentira.CATALOG));
        registro.add("SERVICE_SETTLEMENT_URI", () -> UPSTREAMS.uriDe(UpstreamsDeMentira.SETTLEMENT));
        registro.add("SERVICE_ORDER_URI", () -> UPSTREAMS.uriDe(UpstreamsDeMentira.ORDER));
        registro.add("SERVICE_PAYMENT_URI", () -> UPSTREAMS.uriDe(UpstreamsDeMentira.PAYMENT));
        registro.add("SERVICE_DELIVERY_URI", () -> UPSTREAMS.uriDe(UpstreamsDeMentira.DELIVERY));
        registro.add("SERVICE_CONVERSATION_URI",
                () -> UPSTREAMS.uriDe(UpstreamsDeMentira.CONVERSATION));

        registro.add("JWT_JWKS_URI", IDENTITY::jwksUri);
        registro.add("JWT_ISSUER", () -> IdentityDeMentira.EMISSOR);
        registro.add("JWT_AUDIENCE", () -> IdentityDeMentira.AUDIENCIA);
    }

    @LocalServerPort
    protected int porta;

    protected RestTestClient cliente() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
    }
}
