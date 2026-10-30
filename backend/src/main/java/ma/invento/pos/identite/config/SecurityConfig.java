package ma.invento.pos.identite.config;

import java.util.List;
import ma.invento.pos.identite.application.JetonService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API sans état : JWT porteur sur chaque requête, aucune session HTTP côté serveur. Absente
 * quand l'application tourne sans serveur web (commande {@code --reset-admin}).
 */
@Configuration
@ConditionalOnWebApplication
@EnableMethodSecurity
class SecurityConfig {

    /** Le front n'a besoin que de sa propre origine (aucune police ni script externe). */
    private static final String CSP = "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline';"
            + " object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandler problemes)
            throws Exception {
        http.csrf(csrf -> csrf.disable()) // pas de session ni de cookie d'authentification hors /auth (SameSite=Strict)
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/connexion",
                                "/api/v1/auth/rafraichir",
                                "/api/v1/auth/deconnexion",
                                "/api/v1/auth/secours")
                        .permitAll()
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/**", "/actuator/**")
                        .authenticated()
                        // Le reste est le front React (fichiers statiques).
                        .anyRequest()
                        .permitAll())
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CSP)))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
                        .authenticationEntryPoint(problemes)
                        .accessDeniedHandler(problemes))
                .exceptionHandling(e -> e.authenticationEntryPoint(problemes).accessDeniedHandler(problemes))
                .addFilterAfter(new MotDePasseAChangerFilter(problemes), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private static JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(JetonService.CLAIM_ROLE))));
        return converter;
    }

    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN")
                .implies("RESPONSABLE")
                .role("RESPONSABLE")
                .implies("CAISSIER")
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.corsOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
