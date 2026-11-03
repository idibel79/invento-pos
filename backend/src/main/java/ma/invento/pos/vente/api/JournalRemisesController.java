package ma.invento.pos.vente.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.Duration;
import ma.invento.pos.shared.api.PageResponse;
import ma.invento.pos.vente.infrastructure.JournalRemisesRequete;
import ma.invento.pos.vente.infrastructure.JournalRemisesRequete.Filtre;
import ma.invento.pos.vente.infrastructure.JournalRemisesRequete.Portee;
import ma.invento.pos.vente.infrastructure.JournalRemisesRequete.RemiseJournal;
import ma.invento.pos.vente.infrastructure.JournalRemisesRequete.ResumeRemises;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Journal des remises accordées (ventes validées), consulté par l'admin. Filtres : les {@code jours}
 * derniers jours (absent : tout l'historique), la portée de la remise (ligne ou ticket), la caisse et le
 * vendeur.
 */
@RestController
@RequestMapping("/api/v1/remises")
@PreAuthorize("hasRole('ADMIN')")
@Validated
class JournalRemisesController {

    private final JournalRemisesRequete requete;
    private final Clock clock;

    JournalRemisesController(JournalRemisesRequete requete, Clock clock) {
        this.requete = requete;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    PageResponse<RemiseJournal> lister(
            @RequestParam(required = false) @Min(1) @Max(3660) Integer jours,
            @RequestParam(required = false) Portee portee,
            @RequestParam(required = false) Long caisseId,
            @RequestParam(required = false) Long vendeurId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
        Filtre filtre = filtre(jours, portee, caisseId, vendeurId);
        long total = requete.resumer(filtre).nombre();
        return new PageResponse<>(
                requete.lister(filtre, page, taille), page, taille, total, (int) ((total + taille - 1) / taille));
    }

    @GetMapping("/resume")
    @Transactional(readOnly = true)
    ResumeRemises resumer(
            @RequestParam(required = false) @Min(1) @Max(3660) Integer jours,
            @RequestParam(required = false) Portee portee,
            @RequestParam(required = false) Long caisseId,
            @RequestParam(required = false) Long vendeurId) {
        return requete.resumer(filtre(jours, portee, caisseId, vendeurId));
    }

    private Filtre filtre(Integer jours, Portee portee, Long caisseId, Long vendeurId) {
        return new Filtre(
                jours == null ? null : clock.instant().minus(Duration.ofDays(jours)), portee, caisseId, vendeurId);
    }
}
