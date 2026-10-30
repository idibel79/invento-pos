package ma.invento.pos.vente.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import ma.invento.pos.shared.domain.RegleMetierException;

/** Remise manuelle sur une ligne ou sur le ticket. */
public record Remise(TypeRemise type, BigDecimal valeur) {

    public Remise {
        if (type == null || valeur == null) {
            throw new RegleMetierException("Une remise doit avoir un type et une valeur.");
        }
        if (valeur.signum() < 0) {
            throw new RegleMetierException("Une remise ne peut pas être négative.");
        }
        if (type == TypeRemise.POURCENT && valeur.compareTo(Montants.CENT) > 0) {
            throw new RegleMetierException("Une remise ne peut pas dépasser 100 %.");
        }
    }

    /** Remise nulle ou absente : aucune remise. */
    public static Remise ou(TypeRemise type, BigDecimal valeur) {
        if (type == null || valeur == null || valeur.signum() == 0) return null;
        return new Remise(type, valeur);
    }

    /** Montant de la remise sur une base TTC, au centime ; jamais supérieur à la base. */
    public BigDecimal montantSur(BigDecimal base) {
        BigDecimal montant = type == TypeRemise.POURCENT
                ? base.multiply(valeur).divide(Montants.CENT, 2, RoundingMode.HALF_UP)
                : Montants.arrondir(valeur);
        if (montant.compareTo(base) > 0) {
            throw new RegleMetierException("La remise dépasse le montant auquel elle s'applique.");
        }
        return montant;
    }
}
