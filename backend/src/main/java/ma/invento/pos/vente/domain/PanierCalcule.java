package ma.invento.pos.vente.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Montants d'un panier, calculés uniquement par {@link CalculateurVente}.
 *
 * @param tauxRemiseMax plus forte remise effective d'une ligne (sa remise plus sa part de la remise
 *     ticket), en % de son montant brut : c'est elle qui est comparée aux plafonds de remise
 */
public record PanierCalcule(
        List<LigneCalculee> lignes,
        BigDecimal sousTotal,
        BigDecimal remisesLignes,
        BigDecimal remiseTicket,
        BigDecimal totalHt,
        BigDecimal totalTva,
        BigDecimal totalTtc,
        List<VentilationTva> tva,
        BigDecimal tauxRemiseMax) {

    public BigDecimal totalRemises() {
        return remisesLignes.add(remiseTicket);
    }

    public record LigneCalculee(LignePanier ligne, BigDecimal montantBrut, BigDecimal remiseMontant) {

        public BigDecimal montantNet() {
            return montantBrut.subtract(remiseMontant);
        }
    }

    /** Montants par taux de TVA (les prix publics sont TTC). */
    public record VentilationTva(BigDecimal taux, BigDecimal totalHt, BigDecimal totalTva, BigDecimal totalTtc) {}
}
