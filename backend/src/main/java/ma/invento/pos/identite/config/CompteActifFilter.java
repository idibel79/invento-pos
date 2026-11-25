package ma.invento.pos.identite.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import ma.invento.pos.identite.infrastructure.UtilisateurRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Un compte désactivé perd aussi ses sessions ouvertes : ses jetons de rafraîchissement sont
 * révoqués à la désactivation, et son jeton d'accès encore valide est refusé ici dès la requête
 * suivante (une lecture par clé primaire), sans attendre son expiration.
 */
class CompteActifFilter extends OncePerRequestFilter {

    private final ProblemDetailSecurityHandler problemes;
    private final UtilisateurRepository utilisateurs;

    CompteActifFilter(ProblemDetailSecurityHandler problemes, UtilisateurRepository utilisateurs) {
        this.problemes = problemes;
        this.utilisateurs = utilisateurs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                && !utilisateurs.existsByIdAndActifTrue(
                        Long.parseLong(token.getToken().getSubject()))) {
            SecurityContextHolder.clearContext();
            problemes.ecrire(response, HttpStatus.UNAUTHORIZED, "Authentification requise ou session expirée.");
            return;
        }
        chain.doFilter(request, response);
    }
}
