package ma.invento.pos.parametre.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.parametre.application.ParametreService;
import ma.invento.pos.parametre.application.ParametreService.Parametres;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parametres")
class ParametreController {

    private final ParametreService parametreService;

    ParametreController(ParametreService parametreService) {
        this.parametreService = parametreService;
    }

    /** Lisible par tous : la caisse a besoin des plafonds et de l'en-tête du ticket. */
    @GetMapping
    Parametres lire() {
        return parametreService.lire();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    Parametres modifier(@Valid @RequestBody ParametresCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return parametreService.modifier(
                new Parametres(
                        commande.plafondRemiseCaissier(),
                        commande.plafondRemiseResponsable(),
                        commande.boutiqueNom(),
                        commande.boutiqueAdresse(),
                        commande.fuseauHoraire()),
                UtilisateurConnecte.depuis(jwt).id());
    }

    record ParametresCommande(
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal plafondRemiseCaissier,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal plafondRemiseResponsable,
            @NotNull @Size(max = 120) String boutiqueNom,
            @NotNull @Size(max = 300) String boutiqueAdresse,
            @NotBlank @Size(max = 60) String fuseauHoraire) {}
}
