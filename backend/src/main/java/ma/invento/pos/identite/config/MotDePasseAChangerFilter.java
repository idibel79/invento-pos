package ma.invento.pos.identite.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import ma.invento.pos.identite.application.JetonService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tant que le mot de passe temporaire n'a pas été changé, seuls le changement de mot de passe,
 * la déconnexion et la lecture de son propre profil sont permis.
 */
class MotDePasseAChangerFilter extends OncePerRequestFilter {

    private static final Set<String> CHEMINS_PERMIS =
            Set.of("/api/v1/auth/mot-de-passe", "/api/v1/auth/deconnexion", "/api/v1/auth/moi");

    private final ProblemDetailSecurityHandler problemes;

    MotDePasseAChangerFilter(ProblemDetailSecurityHandler problemes) {
        this.problemes = problemes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                && Boolean.TRUE.equals(token.getToken().getClaimAsBoolean(JetonService.CLAIM_MDP_A_CHANGER))
                && !CHEMINS_PERMIS.contains(request.getRequestURI())) {
            problemes.ecrire(response, HttpStatus.FORBIDDEN, "Changez votre mot de passe temporaire pour continuer.");
            return;
        }
        chain.doFilter(request, response);
    }
}
