package ma.invento.pos.caisse.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Mouvements d'argent du tiroir pendant une session, hors fond de caisse : ventes en espèces et par
 * carte (les tickets TPE comptent comme de l'argent), versements reçus de la caisse principale et
 * versements faits pendant la session.
 */
public record ComptageTiroir(BigDecimal ventesEspeces, BigDecimal ventesCarte, BigDecimal recu, BigDecimal verse) {

    public ComptageTiroir {
        Objects.requireNonNull(ventesEspeces);
        Objects.requireNonNull(ventesCarte);
        Objects.requireNonNull(recu);
        Objects.requireNonNull(verse);
    }

    public BigDecimal ventes() {
        return ventesEspeces.add(ventesCarte);
    }

    /** Attendu dans le tiroir = espèces attendues + tickets TPE attendus. */
    public BigDecimal attendu(BigDecimal fond) {
        return attenduEspeces(fond).add(attenduTpe());
    }

    /** Espèces attendues = fond + ventes espèces + reçu − versé (les versements sont en espèces). */
    public BigDecimal attenduEspeces(BigDecimal fond) {
        return fond.add(ventesEspeces).add(recu).subtract(verse);
    }

    /** Tickets TPE attendus = ventes carte. */
    public BigDecimal attenduTpe() {
        return ventesCarte;
    }

    /**
     * Ce que la clôture ajoute au solde du compte du caissier : ses ventes, plus l'excédent. Un manque
     * ne réduit pas le solde (le caissier le complète de sa poche).
     */
    public static BigDecimal creditCloture(BigDecimal ventes, BigDecimal ecart) {
        return ventes.add(ecart.max(BigDecimal.ZERO));
    }
}
