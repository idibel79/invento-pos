package ma.invento.pos.identite.config;

import java.time.Duration;
import java.util.List;
import ma.invento.pos.identite.application.JetonService;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
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

    private static final long HSTS_DUREE_SECONDES = Duration.ofDays(365).toSeconds();

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, ProblemDetailSecurityHandler problemes, UtilisateurRepository utilisateurs)
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
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        // HTTPS imposé au navigateur pendant un an. Envoyé seulement sur une requête HTTPS
                        // (directe ou X-Forwarded-Proto du proxy) : l'aperçu local en HTTP n'est pas touché.
                        .httpStrictTransportSecurity(hsts ->
                                hsts.maxAgeInSeconds(HSTS_DUREE_SECONDES).includeSubDomains(true))
                        // Aucune adresse de l'application (rubrique dans le #, paramètres) envoyée à un autre site.
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
                        .authenticationEntryPoint(problemes)
                        .accessDeniedHandler(problemes))
                .exceptionHandling(e -> e.authenticationEntryPoint(problemes).accessDeniedHandler(problemes))
                .addFilterAfter(new CompteActifFilter(problemes, utilisateurs), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new MotDePasseAChangerFilter(problemes), CompteActifFilter.class);
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
