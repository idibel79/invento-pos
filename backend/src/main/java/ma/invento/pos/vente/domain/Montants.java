package ma.invento.pos.vente.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Arrondi unique des montants en dirhams : au centime, au plus proche. */
public final class Montants {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    static final BigDecimal CENT = BigDecimal.valueOf(100);

    private Montants() {}

    public static BigDecimal arrondir(BigDecimal montant) {
        return montant.setScale(2, RoundingMode.HALF_UP);
    }

    /** Part en pourcentage (2 décimales) de {@code partie} dans {@code total} ; 0 si le total est nul. */
    static BigDecimal pourcentage(BigDecimal partie, BigDecimal total) {
        if (total.signum() == 0) return ZERO;
        return partie.multiply(CENT).divide(total, 2, RoundingMode.HALF_UP);
    }
}
