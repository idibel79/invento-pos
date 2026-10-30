package ma.invento.pos.vente.domain;

import java.math.BigDecimal;

/**
 * Ligne à calculer : prix public TTC et taux de TVA du produit, remise manuelle facultative.
 *
 * @param remise {@code null} si aucune remise
 */
public record LignePanier(long produitId, int quantite, BigDecimal prixUnitaire, BigDecimal tauxTva, Remise remise) {}
