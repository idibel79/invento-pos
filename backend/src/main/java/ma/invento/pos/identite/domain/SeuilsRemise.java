package ma.invento.pos.identite.domain;

import java.math.BigDecimal;
import ma.invento.pos.shared.domain.RegleMetierException;

/**
 * Remises qu'un responsable peut accorder, fixées par l'admin pour chacun : en % d'une ligne, ou en %
 * du ticket. Une vente porte des remises sur ses lignes ou une remise ticket, jamais les deux.
 */
public record SeuilsRemise(BigDecimal ligne, BigDecimal ticket) {

    // Déclarée avant AUCUN : le constructeur s'en sert pendant l'initialisation de la classe.
    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    /** Aucune remise : valeur par défaut, et seule valeur possible pour un caissier. */
    public static final SeuilsRemise AUCUN = new SeuilsRemise(BigDecimal.ZERO, BigDecimal.ZERO);

    public SeuilsRemise {
        if (ligne == null || ticket == null) {
            throw new RegleMetierException("Les deux seuils de remise sont obligatoires.");
        }
        if (horsBornes(ligne) || horsBornes(ticket)) {
            throw new RegleMetierException("Les seuils de remise doivent être compris entre 0 et 100 %.");
        }
    }

    /** Taux de remise (en %) que ces seuils couvrent, selon que la remise porte sur le ticket ou les lignes. */
    public boolean couvre(BigDecimal taux, boolean remiseTicket) {
        return taux.compareTo(remiseTicket ? ticket : ligne) <= 0;
    }

    public boolean aucun() {
        return ligne.signum() == 0 && ticket.signum() == 0;
    }

    private static boolean horsBornes(BigDecimal valeur) {
        return valeur.signum() < 0 || valeur.compareTo(CENT) > 0;
    }
}
