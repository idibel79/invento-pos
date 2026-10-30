package ma.invento.pos.caisse.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import ma.invento.pos.caisse.application.SessionCaisseService;
import ma.invento.pos.caisse.application.SessionCaisseService.SessionCaisseDto;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sessions de caisse : tout vendeur ouvre et clôture la sienne. */
@RestController
@RequestMapping("/api/v1/sessions")
class SessionCaisseController {

    private final SessionCaisseService sessionService;

    SessionCaisseController(SessionCaisseService sessionService) {
        this.sessionService = sessionService;
    }

    /** Session ouverte de l'utilisateur connecté ; 204 s'il n'en a pas. */
    @GetMapping("/courante")
    ResponseEntity<SessionCaisseDto> courante(@AuthenticationPrincipal Jwt jwt) {
        return sessionService
                .courante(UtilisateurConnecte.depuis(jwt).id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SessionCaisseDto ouvrir(@Valid @RequestBody OuvertureCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return sessionService.ouvrir(commande.caisseId(), commande.fondInitial(), UtilisateurConnecte.depuis(jwt));
    }

    @PostMapping("/{id}/cloture")
    SessionCaisseDto cloturer(
            @PathVariable long id, @Valid @RequestBody ClotureCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return sessionService.cloturer(id, commande.montantCompte(), UtilisateurConnecte.depuis(jwt));
    }

    record OuvertureCommande(
            @NotNull Long caisseId,

            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
            BigDecimal fondInitial) {}

    record ClotureCommande(
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
            BigDecimal montantCompte) {}
}
