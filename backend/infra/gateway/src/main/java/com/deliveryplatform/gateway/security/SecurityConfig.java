package com.deliveryplatform.gateway.security;

import com.deliveryplatform.gateway.config.JwtProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * A cadeia de filtros do gateway — a peça que o {@code CLAUDE.md} listava como
 * <i>"ainda não escrito, requisito do marco 1"</i>.
 *
 * <p><b>O que havia antes disto não era ausência de segurança: era segurança
 * errada.</b> Com o {@code starter-oauth2-resource-server} no classpath e o
 * {@code jwk-set-uri} configurado, e sem nenhuma cadeia declarada, o Spring Boot
 * registra a sua: {@code anyRequest().authenticated()}. O gateway recusaria o
 * login — exigir token para emitir token não fecha — e recusaria os webhooks do
 * PSP, que é a primeira das duas direções de erro que a ADR-012 nomeia.
 *
 * @see <a href="../../../../../../../../../docs/architecture/decisions/ADR-044-a-cadeia-de-filtros-do-gateway.md">ADR-044</a>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * O {@code @Qualifier} não é enfeite: o {@code mvcHandlerMappingIntrospector}
     * do Spring MVC também implementa {@code CorsConfigurationSource}, e
     * injetado por tipo o contexto não sobe — são dois candidatos.
     */
    @Bean
    public SecurityFilterChain cadeia(HttpSecurity http,
                                      JwtDecoder decoder,
                                      @Qualifier("corsConfigurationSource")
                                      CorsConfigurationSource cors) throws Exception {
        http
                // Sem sessão e sem formulário: a credencial é um cabeçalho por
                // requisição. CSRF protege sessão em navegador, e não há
                // sessão — mantê-lo ligado só quebraria POST de webhook.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .cors(c -> c.configurationSource(cors))

                .authorizeHttpRequests(rotas -> rotas
                        // O despacho de erro não recomeça a autorização. Sem
                        // isto, um 500 vira 401 e some a causa verdadeira.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // O portão de entrada. O prefixo inteiro, e não rota a
                        // rota: quem decide o que é público dentro de /auth é o
                        // identity-service, cuja cadeia é
                        // anyRequest().authenticated() por padrão — uma rota
                        // nova sob /auth nasce protegida lá mesmo passando por
                        // aqui. Copiar a lista de lá para cá criaria uma
                        // segunda cópia com a mesma tendência a envelhecer, e
                        // ela JÁ envelheceu uma vez: a ADR-037 fecha a lista
                        // com "nada além disso" e a ADR-042 acrescentou duas
                        // rotas sem voltar lá. (ADR-044 §2)
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // O PSP e o provedor do canal não têm token nosso. A
                        // autenticação deles é assinatura no corpo, conferida
                        // DENTRO do serviço. Prefixo próprio e exclusivo: a
                        // ADR-012 exige que nenhuma outra rota more sob
                        // /webhooks/, para que a exceção seja estreita.
                        .requestMatchers("/api/v1/webhooks/**").permitAll()

                        // Probe de contêiner não carrega credencial, e health
                        // protegido reinicia o serviço em laço.
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()

                        // Inclui /actuator/gateway, que lista as URIs internas
                        // dos oito serviços. Diagnóstico, não informação
                        // pública.
                        .anyRequest().authenticated())

                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder)));

        return http.build();
    }

    /**
     * O decoder montado à mão, para poder receber validadores explícitos.
     *
     * <p>{@code NimbusJwtDecoder.withJwkSetUri(...)} sozinho valida
     * <b>assinatura e tempo, e mais nada</b>. Um token de homologação, assinado
     * pela chave certa, atravessaria a produção inteira. É a mesma falha que a
     * C-A fechou no {@code merchant-service}, e ela estava aberta aqui pelo
     * mesmo motivo: o padrão é permissivo e não avisa.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties propriedades,
                                 @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
                                 String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> audiencia = new JwtClaimValidator<List<String>>(
                "aud",
                aud -> aud != null && aud.contains(propriedades.audience()));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtIssuerValidator(propriedades.issuer()),
                audiencia));

        return decoder;
    }

    /**
     * CORS numa configuração só, e <b>sem credenciais</b>.
     *
     * <p>A credencial deste sistema é um cabeçalho {@code Authorization}, não um
     * cookie. Ligar {@code allowCredentials} não traria nada e abriria a porta
     * para a combinação com origem curinga, que é o erro clássico de CORS.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins}") List<String> origens) {

        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(origens);
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        configuracao.setAllowCredentials(false);
        configuracao.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", configuracao);
        return fonte;
    }
}
