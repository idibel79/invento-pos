package ma.invento.pos.caisse.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import ma.invento.pos.caisse.application.CaisseService;
import ma.invento.pos.caisse.application.CaisseService.CaisseDto;
import ma.invento.pos.caisse.application.SessionCaisseService;
import ma.invento.pos.caisse.application.SessionCaisseService.SuiviCaisseDto;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/caisses")
class CaisseController {

    private final CaisseService caisseService;
    private final SessionCaisseService sessionService;

    CaisseController(CaisseService caisseService, SessionCaisseService sessionService) {
        this.caisseService = caisseService;
        this.sessionService = sessionService;
    }

    @GetMapping
    List<CaisseDto> lister() {
        return caisseService.lister();
    }

    /** Fond de caisse et espèces de chaque caisse ouverte (écran Caisses de l'administration). */
    @GetMapping("/suivi")
    @PreAuthorize("hasRole('ADMIN')")
    List<SuiviCaisseDto> suivi() {
        return sessionService.suivi();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    CaisseDto creer(@Valid @RequestBody CaisseCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return caisseService.creer(
                commande.nom(), UtilisateurConnecte.depuis(jwt).id());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    CaisseDto modifier(
            @PathVariable long id, @Valid @RequestBody CaisseCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return caisseService.modifier(
                id,
                commande.nom(),
                commande.active(),
                UtilisateurConnecte.depuis(jwt).id());
    }

    /** Suppression logique d'une caisse inactive ; son historique est conservé. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void supprimer(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        caisseService.supprimer(id, UtilisateurConnecte.depuis(jwt).id());
    }

    record CaisseCommande(@NotBlank @Size(max = 80) String nom, boolean active) {}
}
