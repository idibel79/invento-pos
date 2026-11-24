package ma.invento.pos.caisse.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Mouvements d'argent du tiroir pendant une session, hors fond de caisse : ventes en espèces et par
 * carte (les tickets TPE comptent comme de l'argent), versements reçus de la caisse principale et
 * versements faits pendant la session. {@code recu} (espèces) et {@code recuTpe} comptent aussi les dettes
 * de clients remboursées pendant la session ; {@code dettesEspeces} est la part des dettes dans {@code recu}.
 * Seule la part des versements qui entre dans le tiroir ou en sort compte ({@code recu}, {@code verseEspeces},
 * {@code verseTpe}) : le reste va sur le compte du caissier ou en vient (argent qu'il garde sur lui).
 */
public record ComptageTiroir(
        BigDecimal ventesEspeces,
        BigDecimal ventesCarte,
        BigDecimal recu,
        BigDecimal recuTpe,
        BigDecimal verseEspeces,
        BigDecimal verseTpe,
        BigDecimal dettesEspeces) {

    public ComptageTiroir {
        Objects.requireNonNull(ventesEspeces);
        Objects.requireNonNull(ventesCarte);
        Objects.requireNonNull(recu);
        Objects.requireNonNull(recuTpe);
        Objects.requireNonNull(verseEspeces);
        Objects.requireNonNull(verseTpe);
        Objects.requireNonNull(dettesEspeces);
    }

    public ComptageTiroir(BigDecimal ventesEspeces, BigDecimal ventesCarte, BigDecimal recu, BigDecimal verse) {
        this(ventesEspeces, ventesCarte, recu, BigDecimal.ZERO, verse, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Versé à la caisse principale depuis le tiroir pendant la session (espèces et tickets TPE). */
    public BigDecimal verse() {
        return verseEspeces.add(verseTpe);
    }

    /** Alimentations reçues de la caisse principale (espèces) : le reçu hors dettes. */
    public BigDecimal alimentations() {
        return recu.subtract(dettesEspeces);
    }

    public BigDecimal ventes() {
        return ventesEspeces.add(ventesCarte);
    }

    /** Attendu dans le tiroir = espèces attendues + tickets TPE attendus. */
    public BigDecimal attendu(BigDecimal fond) {
        return attenduEspeces(fond).add(attenduTpe());
    }

    /** Espèces attendues = fond + ventes espèces + reçu − versé en espèces depuis le tiroir. */
    public BigDecimal attenduEspeces(BigDecimal fond) {
        return fond.add(ventesEspeces).add(recu).subtract(verseEspeces);
    }

    /** Tickets TPE attendus = ventes carte + dettes remboursées par TPE − tickets versés depuis le tiroir. */
    public BigDecimal attenduTpe() {
        return ventesCarte.add(recuTpe).subtract(verseTpe);
    }

    /**
     * Ce que la clôture ajoute au solde du compte du caissier : ses ventes, plus l'excédent. Un manque
     * ne réduit pas le solde (le caissier le complète de sa poche).
     */
    public static BigDecimal creditCloture(BigDecimal ventes, BigDecimal ecart) {
        return ventes.add(ecart.max(BigDecimal.ZERO));
    }
}
