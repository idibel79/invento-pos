package ma.invento.pos.caisse.application;

import java.math.BigDecimal;
import ma.invento.pos.caisse.domain.SessionCaisse;

/**
 * Comptes des caissiers (domaine trésorerie) vus par les sessions de caisse : le fond est pris sur le
 * solde du compte, et la clôture y reporte les ventes, l'excédent et le versement à la caisse
 * principale, dans la transaction de la clôture.
 */
public interface ComptesCaissiers {

    /** Solde du compte : ce que le caissier doit à la caisse principale (ventes de la session ouverte comprises). */
    BigDecimal solde(long utilisateurId);

    /** Versements reçus et faits pendant la session (argent entré dans le tiroir ou sorti), hors clôture. */
    MouvementsSession mouvementsSession(long sessionId);

    /**
     * Clôture par le caissier : le compte reçoit les ventes et l'excédent. Rien ne part vers la caisse
     * principale ; le caissier verse ensuite depuis Mon compte.
     */
    void cloturer(SessionCaisse session, BigDecimal ventes, long auteurId);

    /** Clôture d'office : les ventes sont reportées sur le compte, le tiroir sera compté par l'admin. */
    void cloturerDOffice(SessionCaisse session, BigDecimal ventes, long adminId);

    /** Comptage par l'admin : l'excédent est crédité, {@code montantVerse} entre dans la caisse principale. */
    void terminerComptage(SessionCaisse session, BigDecimal montantVerse, long adminId);

    record MouvementsSession(BigDecimal recu, BigDecimal verse) {
        public static final MouvementsSession AUCUN = new MouvementsSession(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
