package ma.invento.pos.identite.api;

import jakarta.validation.Valid;
import java.util.List;
import ma.invento.pos.identite.api.UtilisateurDtos.CreationUtilisateur;
import ma.invento.pos.identite.api.UtilisateurDtos.ModificationUtilisateur;
import ma.invento.pos.identite.api.UtilisateurDtos.MotDePasseTemporaire;
import ma.invento.pos.identite.api.UtilisateurDtos.PinCommande;
import ma.invento.pos.identite.api.UtilisateurDtos.UtilisateurDto;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.identite.application.UtilisateurService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Écran réservé à l'admin : les comptes ne sont jamais supprimés, seulement désactivés. */
@RestController
@RequestMapping("/api/v1/utilisateurs")
@PreAuthorize("hasRole('ADMIN')")
class UtilisateurController {

    private final UtilisateurService utilisateurService;

    UtilisateurController(UtilisateurService utilisateurService) {
        this.utilisateurService = utilisateurService;
    }

    @GetMapping
    List<UtilisateurDto> lister() {
        return utilisateurService.lister();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UtilisateurDto creer(@Valid @RequestBody CreationUtilisateur commande, @AuthenticationPrincipal Jwt jwt) {
        return utilisateurService.creer(commande, adminId(jwt));
    }

    @PutMapping("/{id}")
    UtilisateurDto modifier(
            @PathVariable long id,
            @Valid @RequestBody ModificationUtilisateur commande,
            @AuthenticationPrincipal Jwt jwt) {
        return utilisateurService.modifier(id, commande, adminId(jwt));
    }

    @PostMapping("/{id}/mot-de-passe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reinitialiserMotDePasse(
            @PathVariable long id,
            @Valid @RequestBody MotDePasseTemporaire commande,
            @AuthenticationPrincipal Jwt jwt) {
        utilisateurService.reinitialiserMotDePasse(id, commande.motDePasseTemporaire(), adminId(jwt));
    }

    @PostMapping("/{id}/deverrouiller")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deverrouiller(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        utilisateurService.deverrouiller(id, adminId(jwt));
    }

    @PutMapping("/{id}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void definirPin(@PathVariable long id, @Valid @RequestBody PinCommande commande, @AuthenticationPrincipal Jwt jwt) {
        utilisateurService.definirPin(id, commande.pin(), adminId(jwt));
    }

    private static long adminId(Jwt jwt) {
        return UtilisateurConnecte.depuis(jwt).id();
    }
}
