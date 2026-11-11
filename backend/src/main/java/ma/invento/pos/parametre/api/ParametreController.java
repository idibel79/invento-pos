package ma.invento.pos.parametre.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    /** Lisible par tous : la caisse a besoin de l'en-tête du ticket. */
    @GetMapping
    Parametres lire() {
        return parametreService.lire();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    Parametres modifier(@Valid @RequestBody ParametresCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return parametreService.modifier(
                new Parametres(
                        commande.boutiqueNom(),
                        commande.boutiqueAdresse(),
                        commande.boutiqueTelephone(),
                        commande.boutiqueEmail(),
                        commande.boutiqueIce(),
                        commande.boutiqueIf(),
                        commande.boutiqueRc(),
                        commande.boutiquePatente(),
                        commande.ticketMessage(),
                        commande.fuseauHoraire()),
                UtilisateurConnecte.depuis(jwt).id());
    }

    record ParametresCommande(
            @NotNull @Size(max = 120) String boutiqueNom,
            @NotNull @Size(max = 300) String boutiqueAdresse,

            @Size(max = 40) @Pattern(regexp = "[0-9+ ().-]*", message = "Téléphone invalide")
            String boutiqueTelephone,

            @Size(max = 120) @Email(message = "Adresse email invalide")
            String boutiqueEmail,

            @Size(max = 20) @Pattern(regexp = "[0-9 ]*", message = "L'ICE ne contient que des chiffres")
            String boutiqueIce,

            @Size(max = 20) String boutiqueIf,
            @Size(max = 30) String boutiqueRc,
            @Size(max = 20) String boutiquePatente,
            @Size(max = 300) String ticketMessage,
            @NotBlank @Size(max = 60) String fuseauHoraire) {}
}
