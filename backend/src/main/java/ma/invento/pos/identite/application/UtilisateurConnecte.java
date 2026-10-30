package ma.invento.pos.identite.application;

import ma.invento.pos.identite.domain.Role;
import org.springframework.security.oauth2.jwt.Jwt;

/** Utilisateur authentifié de la requête, lu depuis son jeton d'accès. */
public record UtilisateurConnecte(long id, String login, Role role) {

    public static UtilisateurConnecte depuis(Jwt jwt) {
        return new UtilisateurConnecte(
                Long.parseLong(jwt.getSubject()),
                jwt.getClaimAsString(JetonService.CLAIM_LOGIN),
                Role.valueOf(jwt.getClaimAsString(JetonService.CLAIM_ROLE)));
    }

    public boolean estAdmin() {
        return role == Role.ADMIN;
    }
}
