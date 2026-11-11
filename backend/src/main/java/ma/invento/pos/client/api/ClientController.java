package ma.invento.pos.client.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import ma.invento.pos.client.api.ClientDtos.AnnulationCommande;
import ma.invento.pos.client.api.ClientDtos.ClientCommande;
import ma.invento.pos.client.api.ClientDtos.ClientDto;
import ma.invento.pos.client.api.ClientDtos.MouvementClientDto;
import ma.invento.pos.client.api.ClientDtos.RemboursementCommande;
import ma.invento.pos.client.api.ClientDtos.SuppressionDto;
import ma.invento.pos.client.application.ClientService;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Annuaire des clients : consulté par tous (choix du client à la caisse, dette et relevé), créé,
 * modifié et supprimé par l'admin seul. Tout utilisateur encaisse un remboursement ; seul l'admin
 * l'annule.
 */
@RestController
@RequestMapping("/api/v1/clients")
@Validated
class ClientController {

    private final ClientService clientService;

    ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    /** @param inactifs inclure les clients désactivés (écran d'administration) */
    @GetMapping
    List<ClientDto> lister(
            @RequestParam(required = false) @Size(max = 120) String recherche,
            @RequestParam(defaultValue = "false") boolean inactifs) {
        return clientService.lister(recherche, inactifs);
    }

    @GetMapping("/{id}/releve")
    List<MouvementClientDto> releve(@PathVariable long id) {
        return clientService.releve(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ClientDto creer(@Valid @RequestBody ClientCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return clientService.creer(commande, UtilisateurConnecte.depuis(jwt).id());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ClientDto modifier(
            @PathVariable long id, @Valid @RequestBody ClientCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return clientService.modifier(
                id, commande, UtilisateurConnecte.depuis(jwt).id());
    }

    /** Supprime un client sans historique ; sinon il est seulement désactivé. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    SuppressionDto supprimer(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return new SuppressionDto(
                clientService.supprimer(id, UtilisateurConnecte.depuis(jwt).id()));
    }

    /** Remboursement de la dette, encaissé par l'utilisateur connecté (crédité sur son compte). */
    @PostMapping("/{id}/remboursements")
    @ResponseStatus(HttpStatus.CREATED)
    MouvementClientDto rembourser(
            @PathVariable long id,
            @Valid @RequestBody RemboursementCommande commande,
            @AuthenticationPrincipal Jwt jwt) {
        UtilisateurConnecte utilisateur = UtilisateurConnecte.depuis(jwt);
        try {
            return clientService.rembourser(id, commande, utilisateur);
        } catch (DataIntegrityViolationException e) {
            // Deux envois simultanés de la même clé : le second renvoie le remboursement enregistré.
            return clientService.rembourser(id, commande, utilisateur);
        }
    }

    @PostMapping("/remboursements/{id}/annulation")
    @PreAuthorize("hasRole('ADMIN')")
    MouvementClientDto annuler(
            @PathVariable long id, @Valid @RequestBody AnnulationCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return clientService.annulerRemboursement(
                id, commande.motif(), UtilisateurConnecte.depuis(jwt).id());
    }
}
