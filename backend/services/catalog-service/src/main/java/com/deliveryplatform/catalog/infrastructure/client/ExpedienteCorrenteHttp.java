package com.deliveryplatform.catalog.infrastructure.client;

import com.deliveryplatform.catalog.application.exception.AcessoNegado;
import com.deliveryplatform.catalog.application.port.out.ExpedienteCorrentePort;
import com.deliveryplatform.catalog.application.port.out.ExpedienteIndisponivel;
import com.deliveryplatform.catalog.config.AutorizacaoProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * A segunda porta HTTP deste serviço para o {@code merchant}, e ela é irmã da
 * primeira.
 *
 * <pre>
 * GET {merchant}/internal/merchants/{estabelecimentoId}/expediente-corrente
 * </pre>
 *
 * <p>A forma é a do {@link AutorizacaoComercialHttp}: o mesmo endereço e os
 * mesmos tempos limite ({@link AutorizacaoProperties} — um host só, uma
 * propriedade só), o token do portador lido do contexto de segurança e
 * reenviado intacto (ADR-045), e {@code exchange} em vez de {@code retrieve},
 * porque cada status tem tratamento próprio.
 *
 * <p><b>Sem cache</b>, ao contrário da autorização: a resposta muda quando a
 * faixa fecha, e cache aqui carimbaria com o expediente que acabou de terminar
 * ({@code estabelecimento.md} §3, "O expediente corrente").
 *
 * <h2>O mapeamento das respostas, que é o que esta classe decide</h2>
 *
 * <table>
 *   <tr><td>{@code 200}</td><td>{@code Optional.of(expedienteDeReferencia)}</td></tr>
 *   <tr><td>{@code 409}</td><td>{@code Optional.empty()} — a loja não abre por
 *       horário. <b>Resposta, não falha</b></td></tr>
 *   <tr><td>{@code 403}</td><td>{@link ExpedienteIndisponivel}. Chegar aqui
 *       significa que a autorização passou e o vínculo não existe do outro
 *       lado — é incoerência entre serviços, não recusa a tratar</td></tr>
 *   <tr><td>{@code 5xx}, tempo esgotado, conexão recusada</td>
 *       <td>{@link ExpedienteIndisponivel} — falha fechada</td></tr>
 * </table>
 *
 * <p><b>O 409 virando vazio é a linha mais importante da classe.</b> Se ele
 * virasse exceção, uma loja sem horário não conseguiria marcar nada; se uma
 * falha de rede virasse vazio, uma queda do {@code merchant} seria lida como
 * "esta loja não abre" e o produto seria gravado sem carimbo — errado, e em
 * silêncio.
 */
@Component
public class ExpedienteCorrenteHttp implements ExpedienteCorrentePort {

    private static final String CAMINHO =
            "/internal/merchants/{estabelecimentoId}/expediente-corrente";

    private final RestClient http;

    public ExpedienteCorrenteHttp(AutorizacaoProperties propriedades) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(propriedades.tempoDeConexao());
        fabrica.setReadTimeout(propriedades.tempoDeLeitura());

        this.http = RestClient.builder()
                .baseUrl(propriedades.merchantUri())
                .requestFactory(fabrica)
                .build();
    }

    @Override
    public Optional<LocalDate> de(UUID estabelecimentoId) {
        String token = tokenDoPortador();

        try {
            return http.get()
                    .uri(CAMINHO, estabelecimentoId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange((requisicao, resposta) -> {
                        int status = resposta.getStatusCode().value();

                        if (status == 200) {
                            return Optional.of(ler(
                                    resposta.bodyTo(RespostaDoMerchant.class), estabelecimentoId));
                        }
                        if (status == 409) {
                            return Optional.<LocalDate>empty();
                        }
                        throw new ExpedienteIndisponivel(
                                "o merchant respondeu " + status + " para o expediente");
                    });

        } catch (ExpedienteIndisponivel jaTraduzida) {
            throw jaTraduzida;
        } catch (RestClientException semResposta) {
            throw new ExpedienteIndisponivel("o merchant não respondeu", semResposta);
        }
    }

    /** O mesmo do {@link AutorizacaoComercialHttp}: sem portador, nega. */
    private static String tokenDoPortador() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacao instanceof JwtAuthenticationToken comJwt) {
            return comJwt.getToken().getTokenValue();
        }
        throw new AcessoNegado();
    }

    /**
     * A data, conferida contra a loja perguntada — pela mesma razão do
     * adaptador vizinho: carimbar com o expediente de outra loja seria
     * invisível.
     */
    private static LocalDate ler(RespostaDoMerchant corpo, UUID estabelecimentoId) {
        if (corpo == null || corpo.expedienteDeReferencia() == null) {
            throw new ExpedienteIndisponivel("o merchant respondeu 200 sem expediente");
        }
        if (!estabelecimentoId.equals(corpo.estabelecimentoId())) {
            throw new ExpedienteIndisponivel(
                    "o merchant respondeu sobre outro estabelecimento");
        }
        return corpo.expedienteDeReferencia();
    }

    /** O corpo do {@code ExpedienteCorrenteResponse} do {@code merchant} (G-C1). */
    record RespostaDoMerchant(UUID estabelecimentoId, LocalDate expedienteDeReferencia) {
    }
}
