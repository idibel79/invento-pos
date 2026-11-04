package ma.invento.pos.caisse.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/** Espèces encaissées pendant une session, fournies par le domaine des ventes. */
public interface EncaissementsSession {

    BigDecimal especesEncaissees(long sessionId);

    /** En une requête ; une session sans vente en espèces est absente de la table. */
    Map<Long, BigDecimal> especesEncaisseesParSession(Collection<Long> sessionIds);
}
