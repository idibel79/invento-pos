package ma.invento.pos.vente.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.function.Supplier;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.vente.api.VenteDtos.MiseEnAttenteCommande;
import ma.invento.pos.vente.api.VenteDtos.PanierCommande;
import ma.invento.pos.vente.api.VenteDtos.PanierDto;
import ma.invento.pos.vente.api.VenteDtos.ValidationCommande;
import ma.invento.pos.vente.api.VenteDtos.VenteDto;
import ma.invento.pos.vente.application.VenteService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ventes de la session ouverte du vendeur. Les envois qui enregistrent une vente portent un en-tête
 * {@code Idempotency-Key} (UUID généré par la caisse) : un renvoi après coupure réseau renvoie la
 * même vente au lieu d'en créer une seconde.
 */
@RestController
@RequestMapping("/api/v1/ventes")
class VenteController {

    private static final String CLE = "Idempotency-Key";
    private static final String FORMAT_CLE = "[A-Za-z0-9-]{8,64}";

    private final VenteService venteService;

    VenteController(VenteService venteService) {
        this.venteService = venteService;
    }

    /** Aperçu du panier calculé par le serveur (totaux, TVA, remises, stock, autorisation). */
    @PostMapping("/calcul")
    PanierDto calculer(@Valid @RequestBody PanierCommande panier, @AuthenticationPrincipal Jwt jwt) {
        return venteService.calculer(panier, UtilisateurConnecte.depuis(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    VenteDto valider(
            @RequestHeader(CLE) @Pattern(regexp = FORMAT_CLE) String cle,
            @Valid @RequestBody ValidationCommande commande,
            @AuthenticationPrincipal Jwt jwt) {
        return rejouable(() -> venteService.valider(commande, cle, UtilisateurConnecte.depuis(jwt)));
    }

    @PostMapping("/attente")
    @ResponseStatus(HttpStatus.CREATED)
    VenteDto mettreEnAttente(
            @RequestHeader(CLE) @Pattern(regexp = FORMAT_CLE) String cle,
            @Valid @RequestBody MiseEnAttenteCommande commande,
            @AuthenticationPrincipal Jwt jwt) {
        return rejouable(() -> venteService.mettreEnAttente(commande, cle, UtilisateurConnecte.depuis(jwt)));
    }

    @GetMapping("/attente")
    List<VenteDto> enAttente(@AuthenticationPrincipal Jwt jwt) {
        return venteService.enAttente(UtilisateurConnecte.depuis(jwt));
    }

    /** Recharge une vente en attente sur sa caisse et prolonge sa réservation de 5 minutes. */
    @PostMapping("/{id}/reprise")
    VenteDto reprendre(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return venteService.reprendre(id, UtilisateurConnecte.depuis(jwt));
    }

    /** Abandonne une vente en attente et libère son stock. */
    @PostMapping("/{id}/abandon")
    VenteDto abandonner(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return venteService.abandonner(id, UtilisateurConnecte.depuis(jwt));
    }

    /**
     * Deux envois simultanés de la même clé (relance pendant que le premier est en cours) : le second
     * bute sur l'unicité de la clé quand le premier est validé. Rejoué, il renvoie la vente enregistrée.
     */
    private static VenteDto rejouable(Supplier<VenteDto> operation) {
        try {
            return operation.get();
        } catch (DataIntegrityViolationException e) {
            return operation.get();
        }
    }
}
