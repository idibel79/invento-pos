package ma.invento.pos.caisse.application;

import java.math.BigDecimal;

/** Espèces encaissées pendant une session, fournies par le domaine des ventes. */
public interface EncaissementsSession {

    BigDecimal especesEncaissees(long sessionId);
}
