package ma.invento.pos.tresorerie.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.YearMonth;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.tresorerie.api.TresorerieDtos.CalendrierDto;
import ma.invento.pos.tresorerie.api.TresorerieDtos.JourneeDto;
import ma.invento.pos.tresorerie.api.TresorerieDtos.MonCompteDto;
import ma.invento.pos.tresorerie.api.TresorerieDtos.RefusCommande;
import ma.invento.pos.tresorerie.api.TresorerieDtos.VersementCommande;
import ma.invento.pos.tresorerie.api.TresorerieDtos.VersementDto;
import ma.invento.pos.tresorerie.application.HistoriqueCompteService;
import ma.invento.pos.tresorerie.application.TresorerieService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compte de l'utilisateur connecté et traitement des versements : chacun ne voit que son compte ; la
 * validation, le refus et l'annulation vérifient qui est le récepteur ou l'émetteur.
 */
@RestController
@RequestMapping("/api/v1/tresorerie")
@Validated
class MonCompteController {

    private final TresorerieService tresorerie;
    private final HistoriqueCompteService historique;

    MonCompteController(TresorerieService tresorerie, HistoriqueCompteService historique) {
        this.tresorerie = tresorerie;
        this.historique = historique;
    }

    @GetMapping("/mon-compte")
    MonCompteDto monCompte(@AuthenticationPrincipal Jwt jwt) {
        return tresorerie.monCompte(UtilisateurConnecte.depuis(jwt).id());
    }

    /** Jours du mois (AAAA-MM) où l'utilisateur connecté a au moins une opération. */
    @GetMapping("/mon-compte/calendrier")
    CalendrierDto calendrier(
            @RequestParam @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String mois, @AuthenticationPrincipal Jwt jwt) {
        return historique.calendrier(UtilisateurConnecte.depuis(jwt).id(), YearMonth.parse(mois));
    }

    /** Journée du compte de l'utilisateur connecté : soldes, totaux et chronologie. */
    @GetMapping("/mon-compte/journee")
    JourneeDto journee(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @AuthenticationPrincipal Jwt jwt) {
        return historique.journee(UtilisateurConnecte.depuis(jwt).id(), jour);
    }

    /** Versement à la caisse principale, en transit jusqu'à sa validation par l'admin. */
    @PostMapping("/mon-compte/versements")
    @ResponseStatus(HttpStatus.CREATED)
    VersementDto verser(@Valid @RequestBody VersementCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return tresorerie.verserALaPrincipale(UtilisateurConnecte.depuis(jwt), commande.montant(), commande.cle());
    }

    /** Le récepteur confirme avoir reçu l'argent. */
    @PostMapping("/versements/{id}/validation")
    VersementDto valider(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return tresorerie.valider(id, UtilisateurConnecte.depuis(jwt));
    }

    @PostMapping("/versements/{id}/refus")
    VersementDto refuser(
            @PathVariable long id, @Valid @RequestBody RefusCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return tresorerie.refuser(id, commande.motif(), UtilisateurConnecte.depuis(jwt));
    }

    /** L'émetteur annule un versement encore en transit. */
    @PostMapping("/versements/{id}/annulation")
    VersementDto annuler(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return tresorerie.annuler(id, UtilisateurConnecte.depuis(jwt));
    }
}
