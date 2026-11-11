package ma.invento.pos.tresorerie.application;

import java.math.BigDecimal;

/**
 * Dettes de clients remboursées pendant une session de caisse (domaine clients), non annulées : l'argent
 * est entré dans le tiroir, en espèces ou en tickets TPE.
 */
public interface RemboursementsDettes {

    Montants pendantLaSession(long sessionId);

    record Montants(BigDecimal especes, BigDecimal tpe) {
        public static final Montants AUCUN = new Montants(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
