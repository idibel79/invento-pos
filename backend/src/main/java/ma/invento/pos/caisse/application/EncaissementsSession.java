package ma.invento.pos.caisse.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/** Encaissements d'une session (ventes validées), fournis par le domaine des ventes. */
public interface EncaissementsSession {

    BigDecimal especesEncaissees(long sessionId);

    /** En une requête ; une session sans vente en espèces est absente de la table. */
    Map<Long, BigDecimal> especesEncaisseesParSession(Collection<Long> sessionIds);

    /** Ventes validées de la session, en espèces et par carte (les tickets TPE comptent comme de l'argent). */
    VentesSession ventes(long sessionId);

    /** En une requête ; une session sans vente est absente de la table. */
    Map<Long, VentesSession> ventesParSession(Collection<Long> sessionIds);

    record VentesSession(BigDecimal especes, BigDecimal carte) {

        public static final VentesSession AUCUNE = new VentesSession(BigDecimal.ZERO, BigDecimal.ZERO);

        public BigDecimal total() {
            return especes.add(carte);
        }
    }
}
