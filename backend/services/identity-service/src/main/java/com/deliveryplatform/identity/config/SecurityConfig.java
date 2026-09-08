package com.deliveryplatform.identity.config;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.deliveryplatform.identity.infrastructure.security.ChaveDeAssinatura;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * A cadeia de filtros que faltava (ADR-037 §1).
 *
 * <p>Sem ela, o padrão do Spring Boot exigia token válido em toda rota —
 * inclusive no {@code /.well-known/jwks.json} que os outros serviços precisam
 * buscar para validar token, e no login que precisa emitir o primeiro. O sistema
 * não conseguia dar a partida em si mesmo, e isso nunca apareceu porque nenhum
 * token jamais foi emitido.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * O par completo, privada inclusa: é o que assina. Quem publica o JWKS
     * projeta a metade pública explicitamente.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(ChaveDeAssinatura chave) {
        return new ImmutableJWKSet<>(new JWKSet(chave.par()));
    }

    /**
     * O decoder é construído <b>em processo</b>, a partir da mesma chave.
     *
     * <p>O {@code application.yml} apontava o {@code jwk-set-uri} deste serviço
     * para ele próprio: ele sairia pela rede atrás da chave pública que tem em
     * memória, e levaria 401 do próprio filtro. A propriedade sai.
     *
     * <p>Os validadores de {@code iss} e {@code aud} são explícitos porque o
     * padrão do Resource Server valida <b>só tempo</b>. Sem eles, os dois claims
     * seriam campo com aparência de controle e nenhum consumidor — o mesmo
     * argumento com que a emenda da ADR-015 apagou o {@code scope}.
     */
    @Bean
    public JwtDecoder jwtDecoder(ChaveDeAssinatura chave, JwtProperties propriedades) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(chave.par().toRSAPublicKey())
                .build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtIssuerValidator(propriedades.issuer()),
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(propriedades.audience()))));

        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * O hash sai prefixado — {bcrypt}$2a$10$… — e é o prefixo que torna
     * verdadeira a frase do usuario.md §3. Trocar para argon2 depois não custa
     * migration nem senha de ninguém: o encoder lê o prefixo antigo, valida, e
     * regrava no formato novo no próximo login.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Condicionada a contexto web servlet: {@code HttpSecurity} só existe onde
     * há um {@code DispatcherServlet} para proteger. Sem esta guarda, todo
     * {@code @SpringBootTest(webEnvironment = NONE)} deste serviço — inclusive
     * os que não têm nada com token — deixa de subir, porque o bean é
     * component-scaneado independente de quem o usa.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        // A cadeia cobre os dispatches request, async e error. Sem esta
                        // linha, todo erro em rota pública é reencaminhado para /error,
                        // refiltrado, e vira 401: um 400 por JSON malformado no login
                        // chegaria ao cliente como credencial inválida -- e a ADR-037 §7,
                        // que manda o login responder 401 para tudo, esconderia o disfarce.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // exigir token para emitir token não fecha
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // é a chave pública; protegê-la trava a partida do sistema inteiro
                        .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                        // probe de contêiner não carrega credencial
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
