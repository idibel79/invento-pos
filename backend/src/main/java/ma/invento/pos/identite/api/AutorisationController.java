package ma.invento.pos.identite.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import ma.invento.pos.identite.application.AutorisationService;
import ma.invento.pos.identite.application.AutorisationService.Autorisateur;
import ma.invento.pos.identite.application.AutorisationService.JetonAutorisation;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Autorisations sur la caisse (remise au-delà du plafond caissier) par PIN d'un responsable. */
@RestController
@RequestMapping("/api/v1/autorisations")
class AutorisationController {

    private final AutorisationService autorisationService;

    AutorisationController(AutorisationService autorisationService) {
        this.autorisationService = autorisationService;
    }

    /** Responsables et admin pouvant autoriser (actifs, PIN défini, non verrouillés). */
    @GetMapping("/autorisateurs")
    List<Autorisateur> autorisateurs() {
        return autorisationService.autorisateurs();
    }

    /** Vérifie le PIN et renvoie un jeton d'autorisation valable 15 minutes pour ce caissier. */
    @PostMapping("/remise")
    JetonAutorisation autoriser(@Valid @RequestBody DemandeAutorisation demande, @AuthenticationPrincipal Jwt jwt) {
        return autorisationService.autoriser(
                demande.autorisateurId(),
                demande.pin(),
                UtilisateurConnecte.depuis(jwt).id());
    }

    record DemandeAutorisation(
            @NotNull Long autorisateurId,
            @NotBlank @Pattern(regexp = "\\d{4}") String pin) {}
}
