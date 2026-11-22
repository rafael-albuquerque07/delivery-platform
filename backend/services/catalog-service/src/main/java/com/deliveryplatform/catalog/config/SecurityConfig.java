package com.deliveryplatform.catalog.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * A cadeia de filtros do {@code catalog} — a terceira do projeto, e ela repete
 * de propósito as duas linhas que a do {@code identity} pagou caro para
 * descobrir e que a do {@code merchant} já repetiu.
 *
 * <p><b>Este serviço não tem rota pública nenhuma.</b> Tudo exige vínculo, e
 * {@code /actuator/health} é a única exceção — probe de contêiner não carrega
 * credencial. O cardápio <i>público</i>, que um dia será aberto, é do marco 7 e
 * vai precisar de uma decisão própria; até lá não existe rota sem token aqui.
 *
 * <p><b>A linha do dispatch de erro é a que ninguém escreve e todo mundo
 * precisa.</b> Sem ela, todo erro é reencaminhado para {@code /error},
 * refiltrado, e vira 401 — um 403 de autorização chegaria ao cliente como "não
 * autenticado", e a recusa que M7 desenhou com cuidado viraria outra coisa.
 *
 * <p><b>Nenhuma linha para {@code /internal/**}</b>, e não por esquecimento:
 * a única rota interna deste serviço, a cotação (ADR-088), exige o token do
 * portador como as outras e cai no {@code anyRequest().authenticated()}. E ele
 * <i>consome</i> as do {@code merchant}.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, AutorizacaoProperties.class,
        CacheDaAutorizacaoProperties.class, DocsProperties.class})
public class SecurityConfig {

    /**
     * Construído à mão, e não pelo autoconfigurador, para pendurar os
     * validadores de {@code iss} e {@code aud}.
     *
     * <p>O decoder padrão valida assinatura e tempo. Um token assinado pela
     * chave certa, com o {@code sub} de alguém, emitido por outro ambiente,
     * passaria — e aqui isso é mais caro do que no {@code merchant}, porque um
     * token aceito aqui é <b>reenviado</b> ao {@code merchant} com o crachá
     * deste serviço na frente.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            JwtProperties propriedades,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtIssuerValidator(propriedades.issuer()),
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(propriedades.audience()))));

        return decoder;
    }

    /**
     * Condicionada a contexto web servlet, como as outras duas: sem esta
     * guarda, todo {@code @SpringBootTest(webEnvironment = NONE)} deste serviço
     * — os de persistência, que não têm nada com token — deixa de subir, porque
     * o bean é component-scaneado independentemente de quem o usa.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain filtros(HttpSecurity http, DocsProperties docs) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> {
                    rotas.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    rotas.requestMatchers("/actuator/health/**").permitAll();
                    if (docs.abertas()) {
                        // ADR-050: só no computador de quem desenvolve, e o
                        // padrão da propriedade é false — esquecer fecha.
                        rotas.requestMatchers(
                                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                                .permitAll();
                    }
                    rotas.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
