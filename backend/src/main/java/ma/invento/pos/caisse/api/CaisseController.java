package ma.invento.pos.caisse.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import ma.invento.pos.caisse.application.CaisseService;
import ma.invento.pos.caisse.application.CaisseService.CaisseDto;
import ma.invento.pos.identite.application.UtilisateurConnecte;
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

@RestController
@RequestMapping("/api/v1/caisses")
class CaisseController {

    private final CaisseService caisseService;

    CaisseController(CaisseService caisseService) {
        this.caisseService = caisseService;
    }

    @GetMapping
    List<CaisseDto> lister() {
        return caisseService.lister();
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

    record CaisseCommande(@NotBlank @Size(max = 80) String nom, boolean active) {}
}
