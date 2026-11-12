package ma.invento.pos.facture.api;

import ma.invento.pos.facture.api.FactureDtos.FactureDto;
import ma.invento.pos.facture.application.FactureService;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Facture A4 d'une vente avec client, pour tout utilisateur : en fin de vente et depuis le relevé du
 * client, lui aussi visible par tous.
 */
@RestController
@RequestMapping("/api/v1/ventes")
class FactureController {

    private final FactureService factureService;

    FactureController(FactureService factureService) {
        this.factureService = factureService;
    }

    /** Émet la facture à la première demande (numéro attribué), puis renvoie toujours la même. */
    @PostMapping("/{id}/facture")
    FactureDto facturer(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return factureService.facturer(id, UtilisateurConnecte.depuis(jwt).id());
    }
}
