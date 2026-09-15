package com.deliveryplatform.merchant.config;

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
 * A cadeia de filtros do {@code merchant} — a segunda do projeto, e ela repete
 * de propósito duas linhas que a do {@code identity} pagou caro para descobrir.
 *
 * <p><b>Este serviço não tem rota pública nenhuma</b>, e a diferença para o
 * {@code identity} é a razão de ser dos dois: lá havia o nó de precisar emitir o
 * primeiro token sem ter token; aqui não há nó nenhum. Tudo exige vínculo, e
 * {@code /actuator/health} é a única exceção — probe de contêiner não carrega
 * credencial.
 *
 * <p><b>A linha do dispatch de erro é a que ninguém escreve e todo mundo
 * precisa.</b> A cadeia cobre request, async e error: sem ela, todo erro é
 * reencaminhado para {@code /error}, refiltrado, e vira 401 — um 400 por JSON
 * malformado chegaria ao cliente como "não autenticado", e o defeito de quem
 * chama ficaria escondido atrás da política de segurança.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * Construído à mão, e não pelo autoconfigurador, por um motivo só: para
     * pendurar os validadores de {@code iss} e {@code aud}.
     *
     * <p>O decoder padrão do Spring Boot valida assinatura e tempo. Um token
     * assinado pela chave certa, com o {@code sub} de alguém, emitido por outro
     * ambiente — homologação, por exemplo — passaria. É o tipo de furo que só
     * aparece quando dois ambientes compartilham chave por engano, que é
     * exatamente quando ninguém está olhando.
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
     * Condicionada a contexto web servlet, como a do {@code identity}: sem esta
     * guarda, todo {@code @SpringBootTest(webEnvironment = NONE)} deste serviço
     * — os cinco de persistência, que não têm nada com token — deixa de subir,
     * porque o bean é component-scaneado independentemente de quem o usa.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
