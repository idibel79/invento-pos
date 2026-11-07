package ma.invento.pos.vente.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.ZoneId;
import ma.invento.pos.parametre.application.ParametreService;
import ma.invento.pos.shared.api.PageResponse;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import ma.invento.pos.vente.infrastructure.HistoriqueVentesRequete;
import ma.invento.pos.vente.infrastructure.HistoriqueVentesRequete.DetailVente;
import ma.invento.pos.vente.infrastructure.HistoriqueVentesRequete.Filtre;
import ma.invento.pos.vente.infrastructure.HistoriqueVentesRequete.ResumeVentes;
import ma.invento.pos.vente.infrastructure.HistoriqueVentesRequete.VenteHistorique;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historique des ventes validées, consulté par l'admin. Filtres : un jour précis dans le fuseau de la
 * boutique (absent : tout l'historique), la caisse et le vendeur.
 */
@RestController
@RequestMapping("/api/v1/historique-ventes")
@PreAuthorize("hasRole('ADMIN')")
@Validated
class HistoriqueVentesController {

    private final HistoriqueVentesRequete requete;
    private final ParametreService parametres;

    HistoriqueVentesController(HistoriqueVentesRequete requete, ParametreService parametres) {
        this.requete = requete;
        this.parametres = parametres;
    }

    @GetMapping
    @Transactional(readOnly = true)
    PageResponse<VenteHistorique> lister(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam(required = false) Long caisseId,
            @RequestParam(required = false) Long vendeurId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
        Filtre filtre = filtre(jour, caisseId, vendeurId);
        long total = requete.resumer(filtre).nombre();
        return new PageResponse<>(
                requete.lister(filtre, page, taille), page, taille, total, (int) ((total + taille - 1) / taille));
    }

    @GetMapping("/resume")
    @Transactional(readOnly = true)
    ResumeVentes resumer(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam(required = false) Long caisseId,
            @RequestParam(required = false) Long vendeurId) {
        return requete.resumer(filtre(jour, caisseId, vendeurId));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    DetailVente detail(@PathVariable long id) {
        return requete.detail(id).orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable."));
    }

    /** Le jour s'entend dans le fuseau de la boutique : de minuit à minuit, heure locale. */
    private Filtre filtre(LocalDate jour, Long caisseId, Long vendeurId) {
        if (jour == null) return new Filtre(null, null, caisseId, vendeurId);
        ZoneId fuseau = ZoneId.of(parametres.lire().fuseauHoraire());
        return new Filtre(
                jour.atStartOfDay(fuseau).toInstant(),
                jour.plusDays(1).atStartOfDay(fuseau).toInstant(),
                caisseId,
                vendeurId);
    }
}
