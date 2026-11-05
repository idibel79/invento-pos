package ma.invento.pos.tresorerie.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.shared.api.PageResponse;
import ma.invento.pos.tresorerie.api.TresorerieDtos.ComptesDto;
import ma.invento.pos.tresorerie.api.TresorerieDtos.OperationCommande;
import ma.invento.pos.tresorerie.api.TresorerieDtos.VersementCaissierCommande;
import ma.invento.pos.tresorerie.api.TresorerieDtos.VersementDto;
import ma.invento.pos.tresorerie.application.TresorerieService;
import ma.invento.pos.tresorerie.infrastructure.JournalTresorerieJdbc.FiltreStatut;
import ma.invento.pos.tresorerie.infrastructure.JournalTresorerieJdbc.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Caisse principale, comptes des caissiers et journal : réservés à l'admin. */
@RestController
@RequestMapping("/api/v1/tresorerie")
@PreAuthorize("hasRole('ADMIN')")
@Validated
class CaissePrincipaleController {

    private final TresorerieService tresorerie;

    CaissePrincipaleController(TresorerieService tresorerie) {
        this.tresorerie = tresorerie;
    }

    @GetMapping("/comptes")
    ComptesDto comptes() {
        return tresorerie.comptes();
    }

    /** Versements des caissiers en transit vers la caisse principale. */
    @GetMapping("/principale/a-valider")
    List<VersementDto> aValider() {
        return tresorerie.aValiderPrincipale();
    }

    /** Versements du jour ou encore en transit d'un caissier. */
    @GetMapping("/comptes/{utilisateurId}/versements")
    List<VersementDto> versementsDuCaissier(@PathVariable long utilisateurId) {
        return tresorerie.versementsDuCaissier(utilisateurId);
    }

    @PostMapping("/principale/alimentation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void alimenter(@Valid @RequestBody OperationCommande commande, @AuthenticationPrincipal Jwt jwt) {
        tresorerie.alimenter(
                commande.montant(),
                commande.motif(),
                commande.cle(),
                UtilisateurConnecte.depuis(jwt).id());
    }

    @PostMapping("/principale/retrait")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retirer(@Valid @RequestBody OperationCommande commande, @AuthenticationPrincipal Jwt jwt) {
        tresorerie.retirer(
                commande.montant(),
                commande.motif(),
                commande.cle(),
                UtilisateurConnecte.depuis(jwt).id());
    }

    @PostMapping("/principale/versements")
    @ResponseStatus(HttpStatus.CREATED)
    VersementDto verserAuCaissier(
            @Valid @RequestBody VersementCaissierCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return tresorerie.verserAuCaissier(
                commande.utilisateurId(),
                commande.montant(),
                commande.motif(),
                commande.cle(),
                UtilisateurConnecte.depuis(jwt).id());
    }

    /** Passage en perte d'une partie du solde d'un caissier. */
    @PostMapping("/comptes/{utilisateurId}/ajustement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void ajuster(
            @PathVariable long utilisateurId,
            @Valid @RequestBody OperationCommande commande,
            @AuthenticationPrincipal Jwt jwt) {
        tresorerie.ajuster(
                utilisateurId,
                commande.montant(),
                commande.motif(),
                commande.cle(),
                UtilisateurConnecte.depuis(jwt).id());
    }

    /** Journal de la caisse principale : filtres jour (fuseau de la boutique), caisse, utilisateur, statut. */
    @GetMapping("/journal")
    PageResponse<Operation> journal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam(required = false) Long caisseId,
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) FiltreStatut statut,
            @RequestParam(defaultValue = "false") boolean croissant,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
        return tresorerie.journal(jour, caisseId, utilisateurId, statut, croissant, page, taille);
    }
}
