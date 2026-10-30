package ma.invento.pos.catalogue.api;

import jakarta.validation.Valid;
import java.util.List;
import ma.invento.pos.catalogue.api.CatalogueDtos.ProduitCommande;
import ma.invento.pos.catalogue.api.CatalogueDtos.ProduitDto;
import ma.invento.pos.catalogue.api.CatalogueDtos.ReferenceCommande;
import ma.invento.pos.catalogue.api.CatalogueDtos.ReferenceDto;
import ma.invento.pos.catalogue.api.CatalogueDtos.TvaDto;
import ma.invento.pos.catalogue.application.CatalogueService;
import ma.invento.pos.catalogue.infrastructure.ProduitSpecifications.Disponibilite;
import ma.invento.pos.identite.application.UtilisateurConnecte;
import ma.invento.pos.shared.api.PageResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Catalogue : lecture pour toutes les caisses, modification réservée à l'admin. */
@RestController
@RequestMapping("/api/v1")
class CatalogueController {

    private final CatalogueService catalogueService;

    CatalogueController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    /**
     * Recherche paginée. {@code recherche} couvre la désignation (contient), le PID et le
     * code-barre (exacts) ; les produits en rupture restent listés (affichés grisés).
     */
    @GetMapping("/produits")
    PageResponse<ProduitDto> rechercher(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Long categorieId,
            @RequestParam(required = false) Disponibilite disponibilite,
            @RequestParam(required = false) Boolean actif,
            @ParameterObject @PageableDefault(size = 50, sort = "designation", direction = Sort.Direction.ASC)
                    Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return catalogueService.rechercher(
                recherche, zoneId, categorieId, disponibilite, actif, pageable, estAdmin(jwt));
    }

    @GetMapping("/produits/{id}")
    ProduitDto parId(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return catalogueService.parId(id, estAdmin(jwt));
    }

    /** Scan de douchette : réponse immédiate, un seul aller-retour. */
    @GetMapping("/produits/code-barre/{code}")
    ProduitDto parCodeBarre(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        return catalogueService.parCodeBarre(code, estAdmin(jwt));
    }

    @PostMapping("/produits")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ProduitDto creer(@Valid @RequestBody ProduitCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return catalogueService.creer(commande, UtilisateurConnecte.depuis(jwt).id());
    }

    @PutMapping("/produits/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ProduitDto modifier(
            @PathVariable long id, @Valid @RequestBody ProduitCommande commande, @AuthenticationPrincipal Jwt jwt) {
        return catalogueService.modifier(
                id, commande, UtilisateurConnecte.depuis(jwt).id());
    }

    @GetMapping("/categories")
    List<ReferenceDto> categories() {
        return catalogueService.categories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ReferenceDto creerCategorie(@Valid @RequestBody ReferenceCommande commande) {
        return catalogueService.creerCategorie(commande.nom());
    }

    @GetMapping("/zones")
    List<ReferenceDto> zones() {
        return catalogueService.zones();
    }

    @PostMapping("/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ReferenceDto creerZone(@Valid @RequestBody ReferenceCommande commande) {
        return catalogueService.creerZone(commande.nom());
    }

    @GetMapping("/tvas")
    List<TvaDto> tvas() {
        return catalogueService.tvas();
    }

    private static boolean estAdmin(Jwt jwt) {
        return UtilisateurConnecte.depuis(jwt).estAdmin();
    }
}
