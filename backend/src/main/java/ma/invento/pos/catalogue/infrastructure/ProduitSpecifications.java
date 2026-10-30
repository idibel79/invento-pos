package ma.invento.pos.catalogue.infrastructure;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ma.invento.pos.catalogue.domain.CodeBarre;
import ma.invento.pos.catalogue.domain.Produit;
import org.springframework.data.jpa.domain.Specification;

/** Filtres de recherche du catalogue, combinables. */
public final class ProduitSpecifications {

    public enum Disponibilite {
        EN_STOCK,
        RUPTURE
    }

    private ProduitSpecifications() {}

    public static Specification<Produit> criteres(
            String recherche, Long zoneId, Long categorieId, Disponibilite disponibilite, Boolean actif) {
        List<Specification<Produit>> specs = new ArrayList<>();
        if (recherche != null && !recherche.isBlank()) specs.add(recherche(recherche.trim()));
        if (zoneId != null)
            specs.add((root, query, cb) -> cb.equal(root.get("zone").get("id"), zoneId));
        if (categorieId != null)
            specs.add((root, query, cb) -> cb.equal(root.get("categorie").get("id"), categorieId));
        if (disponibilite != null) specs.add(disponibilite(disponibilite));
        if (actif != null) specs.add((root, query, cb) -> cb.equal(root.get("actif"), actif));
        return Specification.allOf(specs);
    }

    /** Désignation qui contient le texte, ou PID / code-barre exact (saisie ou scan). */
    private static Specification<Produit> recherche(String texte) {
        String motif = "%" + echapperLike(texte.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            Subquery<Long> parCode = query.subquery(Long.class);
            var produit = parCode.from(Produit.class);
            Join<Produit, CodeBarre> codes = produit.join("codesBarres");
            parCode.select(produit.get("id")).where(cb.equal(codes.get("code"), texte));
            return cb.or(
                    cb.like(cb.lower(root.get("designation")), motif, '\\'),
                    cb.equal(root.get("pid"), texte),
                    root.get("id").in(parCode));
        };
    }

    private static Specification<Produit> disponibilite(Disponibilite disponibilite) {
        return (root, query, cb) -> {
            var disponible = cb.diff(root.<Integer>get("stock"), root.<Integer>get("stockReserve"));
            return disponibilite == Disponibilite.EN_STOCK ? cb.gt(disponible, 0) : cb.le(disponible, 0);
        };
    }

    private static String echapperLike(String texte) {
        return texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
