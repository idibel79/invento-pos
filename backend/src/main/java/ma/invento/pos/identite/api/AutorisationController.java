package ma.invento.pos.identite.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import ma.invento.pos.identite.application.AutorisationService;
import ma.invento.pos.identite.application.AutorisationService.Autorisateur;
import ma.invento.pos.identite.application.AutorisationService.JetonAutorisation;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.shared.domain.ObjetAutorisation;
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

    /** Vérifie le PIN et renvoie un jeton valable 15 minutes pour ce caissier et cette seule remise. */
    @PostMapping("/remise")
    JetonAutorisation autoriser(@Valid @RequestBody DemandeAutorisation demande, @AuthenticationPrincipal Jwt jwt) {
        return autorisationService.autoriser(
                demande.autorisateurId(),
                demande.pin(),
                UtilisateurConnecte.depuis(jwt).id(),
                ObjetAutorisation.remise(demande.produitId(), demande.remiseType(), demande.remiseValeur()));
    }

    /**
     * @param produitId article remisé, ou absent pour une remise sur le ticket : chaque remise demande
     *     sa propre autorisation
     */
    record DemandeAutorisation(
            @NotNull Long autorisateurId,
            @NotBlank @Pattern(regexp = "\\d{4}") String pin,
            @Schema(nullable = true) Long produitId,
            @NotNull @Pattern(regexp = "POURCENT|MONTANT") String remiseType,

            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
            BigDecimal remiseValeur) {}
}
