package ma.invento.pos.identite.api;

import jakarta.validation.Valid;
import ma.invento.pos.identite.api.UtilisateurDtos.ChangementMotDePasse;
import ma.invento.pos.identite.api.UtilisateurDtos.CodeSecoursDto;
import ma.invento.pos.identite.api.UtilisateurDtos.Connexion;
import ma.invento.pos.identite.api.UtilisateurDtos.RecuperationCommande;
import ma.invento.pos.identite.api.UtilisateurDtos.RecuperationDto;
import ma.invento.pos.identite.api.UtilisateurDtos.SessionDto;
import ma.invento.pos.identite.api.UtilisateurDtos.UtilisateurDto;
import ma.invento.pos.identite.application.AuthService;
import ma.invento.pos.identite.application.AuthService.RecuperationAdmin;
import ma.invento.pos.identite.application.AuthService.Session;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.identite.config.SecurityProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    static final String COOKIE_REFRESH = "pos_refresh";
    private static final String CHEMIN_COOKIE = "/api/v1/auth";

    private final AuthService authService;
    private final SecurityProperties properties;

    AuthController(AuthService authService, SecurityProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/connexion")
    ResponseEntity<SessionDto> connexion(@Valid @RequestBody Connexion connexion) {
        return reponse(authService.connecter(connexion.login(), connexion.motDePasse()));
    }

    @PostMapping("/rafraichir")
    ResponseEntity<SessionDto> rafraichir(@CookieValue(name = COOKIE_REFRESH, required = false) String jeton) {
        return reponse(authService.rafraichir(jeton));
    }

    @PostMapping("/deconnexion")
    ResponseEntity<Void> deconnexion(@CookieValue(name = COOKIE_REFRESH, required = false) String jeton) {
        authService.deconnecter(jeton);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", 0).toString())
                .build();
    }

    @PostMapping("/mot-de-passe")
    ResponseEntity<SessionDto> changerMotDePasse(
            @Valid @RequestBody ChangementMotDePasse commande, @AuthenticationPrincipal Jwt jwt) {
        return reponse(authService.changerMotDePasse(
                UtilisateurConnecte.depuis(jwt).id(), commande.motDePasseActuel(), commande.nouveauMotDePasse()));
    }

    /** Mot de passe admin oublié : code de secours remis à l'installation (usage unique). */
    @PostMapping("/secours")
    RecuperationDto secours(@Valid @RequestBody RecuperationCommande commande) {
        RecuperationAdmin recuperation =
                authService.recupererAdmin(commande.codeSecours(), commande.nouveauMotDePasse());
        return new RecuperationDto(recuperation.login(), recuperation.nouveauCodeSecours());
    }

    /** Génère un nouveau code de secours (l'ancien est invalidé) ; il n'est affiché qu'ici. */
    @PostMapping("/code-secours")
    @PreAuthorize("hasRole('ADMIN')")
    CodeSecoursDto nouveauCodeSecours(@AuthenticationPrincipal Jwt jwt) {
        return new CodeSecoursDto(authService.renouvelerCodeSecours(
                UtilisateurConnecte.depuis(jwt).id()));
    }

    @GetMapping("/moi")
    UtilisateurDto moi(@AuthenticationPrincipal Jwt jwt) {
        return authService.profil(UtilisateurConnecte.depuis(jwt).id());
    }

    private ResponseEntity<SessionDto> reponse(Session session) {
        var jetons = session.jetons();
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookie(
                                        jetons.rafraichissement(),
                                        properties.refreshTokenTtl().toSeconds())
                                .toString())
                .body(new SessionDto(jetons.acces(), jetons.expireDansSecondes(), session.utilisateur()));
    }

    private ResponseCookie cookie(String valeur, long maxAgeSecondes) {
        return ResponseCookie.from(COOKIE_REFRESH, valeur)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(CHEMIN_COOKIE)
                .maxAge(maxAgeSecondes)
                .build();
    }
}
