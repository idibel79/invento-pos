package ma.invento.pos.vente.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.ZoneId;
import ma.invento.pos.parametre.application.ParametreService;
import ma.invento.pos.shared.api.PageResponse;
import ma.invento.pos.vente.infrastructure.JournalRemisesRequete;
import ma.invento.pos.vente.infrastructure.JournalRemisesRequete.Filtre;
import ma.invento.pos.vente.infrastructure.JournalRemisesRequete.Portee;
import ma.invento.pos.vente.infrastructure.JournalRemisesRequete.RemiseJournal;
import ma.invento.pos.vente.infrastructure.JournalRemisesRequete.ResumeRemises;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Journal des remises accordées (ventes validées). Filtres : un {@code jour} dans le fuseau de la boutique
 * (absent : tout l'historique), la portée de la remise (ligne ou ticket), la caisse et le vendeur. L'admin voit
 * toutes les remises ; les autres rôles seulement celles de leurs propres ventes.
 */
@RestController
@RequestMapping("/api/v1/remises")
@Validated
class JournalRemisesController {

    private final JournalRemisesRequete requete;
    private final ParametreService parametres;

    JournalRemisesController(JournalRemisesRequete requete, ParametreService parametres) {
        this.requete = requete;
        this.parametres = parametres;
    }

    @GetMapping
    @Transactional(readOnly = true)
    PageResponse<RemiseJournal> lister(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam(required = false) Portee portee,
            @RequestParam(required = false) Long caisseId,
            @RequestParam(required = false) Long vendeurId,
            @RequestParam(required = false) @Size(max = 100) String client,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
        Filtre filtre = filtre(jwt, jour, portee, caisseId, vendeurId, client);
        long total = requete.resumer(filtre).nombre();
        return new PageResponse<>(
                requete.lister(filtre, page, taille), page, taille, total, (int) ((total + taille - 1) / taille));
    }

    @GetMapping("/resume")
    @Transactional(readOnly = true)
    ResumeRemises resumer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam(required = false) Portee portee,
            @RequestParam(required = false) Long caisseId,
            @RequestParam(required = false) Long vendeurId,
            @RequestParam(required = false) @Size(max = 100) String client) {
        return requete.resumer(filtre(jwt, jour, portee, caisseId, vendeurId, client));
    }

    /** Le jour s'entend dans le fuseau de la boutique : de minuit à minuit, heure locale. */
    private Filtre filtre(Jwt jwt, LocalDate jour, Portee portee, Long caisseId, Long vendeurId, String client) {
        Long vendeur = HistoriqueVentesController.vendeurAutorise(jwt, vendeurId);
        if (jour == null) return new Filtre(null, null, portee, caisseId, vendeur, client);
        ZoneId fuseau = ZoneId.of(parametres.lire().fuseauHoraire());
        return new Filtre(
                jour.atStartOfDay(fuseau).toInstant(),
                jour.plusDays(1).atStartOfDay(fuseau).toInstant(),
                portee,
                caisseId,
                vendeur,
                client);
    }
}
