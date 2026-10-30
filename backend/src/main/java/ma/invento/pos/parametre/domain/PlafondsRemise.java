package ma.invento.pos.parametre.domain;

import java.math.BigDecimal;
import ma.invento.pos.shared.domain.RegleMetierException;

/**
 * Plafonds de remise en pourcentage (cadrage § 1) : au-delà du plafond caissier, PIN du
 * responsable ; au-delà du plafond responsable, admin. Invariant : 0 ≤ caissier ≤ responsable ≤ 100.
 */
public record PlafondsRemise(BigDecimal caissier, BigDecimal responsable) {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    public PlafondsRemise {
        if (caissier == null || responsable == null) {
            throw new RegleMetierException("Les deux plafonds de remise sont obligatoires.");
        }
        if (caissier.signum() < 0 || responsable.compareTo(CENT) > 0) {
            throw new RegleMetierException("Les plafonds de remise doivent être compris entre 0 et 100 %.");
        }
        if (caissier.compareTo(responsable) > 0) {
            throw new RegleMetierException("Le plafond caissier ne peut pas dépasser le plafond responsable.");
        }
    }
}
