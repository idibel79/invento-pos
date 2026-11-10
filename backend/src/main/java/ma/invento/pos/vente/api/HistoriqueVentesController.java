package ma.invento.pos.vente.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.ZoneId;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.parametre.application.ParametreService;
import ma.invento.pos.shared.api.PageResponse;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import ma.invento.pos.vente.api.VenteDtos.VenteDto;
import ma.invento.pos.vente.application.VenteService;
import ma.invento.pos.vente.infrastructure.HistoriqueVentesRequete;
import ma.invento.pos.vente.infrastructure.HistoriqueVentesRequete.DetailVente;
import ma.invento.pos.vente.infrastructure.HistoriqueVentesRequete.Filtre;
import ma.invento.pos.vente.infrastructure.HistoriqueVentesRequete.ResumeVentes;
import ma.invento.pos.vente.infrastructure.HistoriqueVentesRequete.VenteHistorique;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historique des ventes validées. Filtres : un jour précis dans le fuseau de la boutique (absent : tout
 * l'historique), la caisse et le vendeur. L'admin voit toutes les ventes ; les autres rôles seulement les
 * leurs, quel que soit le vendeur demandé.
 */
@RestController
@RequestMapping("/api/v1/historique-ventes")
@Validated
class HistoriqueVentesController {

    private final HistoriqueVentesRequete requete;
    private final ParametreService parametres;
    private final VenteService ventes;

    HistoriqueVentesController(HistoriqueVentesRequete requete, ParametreService parametres, VenteService ventes) {
        this.requete = requete;
        this.parametres = parametres;
        this.ventes = ventes;
    }

    @GetMapping
    @Transactional(readOnly = true)
    PageResponse<VenteHistorique> lister(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam(required = false) Long caisseId,
            @RequestParam(required = false) Long vendeurId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
        Filtre filtre = filtre(jour, caisseId, vendeurAutorise(jwt, vendeurId));
        long total = requete.resumer(filtre).nombre();
        return new PageResponse<>(
                requete.lister(filtre, page, taille), page, taille, total, (int) ((total + taille - 1) / taille));
    }

    @GetMapping("/resume")
    @Transactional(readOnly = true)
    ResumeVentes resumer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam(required = false) Long caisseId,
            @RequestParam(required = false) Long vendeurId) {
        return requete.resumer(filtre(jour, caisseId, vendeurAutorise(jwt, vendeurId)));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    DetailVente detail(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return requete.detail(id, vendeurAutorise(jwt, null))
                .orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable."));
    }

    /** Vente validée au format du ticket de caisse, pour le réimprimer. */
    @GetMapping("/{id}/ticket")
    VenteDto ticket(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return ventes.venteValidee(id, UtilisateurConnecte.depuis(jwt));
    }

    /** Vendeur à filtrer : celui demandé pour l'admin, l'utilisateur lui-même pour les autres rôles. */
    static Long vendeurAutorise(Jwt jwt, Long vendeurId) {
        UtilisateurConnecte utilisateur = UtilisateurConnecte.depuis(jwt);
        return utilisateur.estAdmin() ? vendeurId : Long.valueOf(utilisateur.id());
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
