package ma.invento.pos.tresorerie.domain;

import java.math.BigDecimal;
import ma.invento.pos.shared.domain.RegleMetierException;

/** Règles communes aux opérations de trésorerie. */
public final class ReglesTresorerie {

    public static final int MOTIF_MAX = 300;

    private ReglesTresorerie() {}

    /** Montant d'une opération : strictement positif, au centime. */
    public static void verifierMontant(BigDecimal montant) {
        if (montant == null || montant.signum() <= 0) {
            throw new RegleMetierException("Le montant doit être supérieur à 0.");
        }
        if (montant.stripTrailingZeros().scale() > 2) {
            throw new RegleMetierException("Le montant se saisit au centime près.");
        }
    }

    /** Motif nettoyé ; obligatoire pour un retrait, un refus ou un ajustement. */
    public static String motif(String motif, boolean obligatoire) {
        String propre = motif == null ? "" : motif.strip();
        if (propre.isEmpty()) {
            if (obligatoire) throw new RegleMetierException("Le motif est obligatoire.");
            return null;
        }
        if (propre.length() > MOTIF_MAX) {
            throw new RegleMetierException("Le motif ne doit pas dépasser " + MOTIF_MAX + " caractères.");
        }
        return propre;
    }

    /** Les versements vont d'un caissier à la caisse principale ou l'inverse, jamais de caissier à caissier. */
    public static void verifierSens(TypeCompte emetteur, TypeCompte recepteur) {
        if ((emetteur == TypeCompte.PRINCIPALE) == (recepteur == TypeCompte.PRINCIPALE)) {
            throw new RegleMetierException("Un versement relie un caissier et la caisse principale.");
        }
    }
}
