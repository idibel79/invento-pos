package ma.invento.pos.tresorerie.domain;

import java.math.BigDecimal;
import java.util.Objects;
import ma.invento.pos.shared.domain.RegleMetierException;

/**
 * Ce qu'un caissier peut verser à la caisse principale, par source et par mode. Caisse ouverte, l'argent est
 * soit dans le tiroir (fond, ventes de la session comprises, reçu − déjà versé depuis le tiroir), soit sur
 * lui : son dû, ventes de la session comprises, moins ce qui est dans le tiroir. Sans caisse ouverte, tout est
 * sur lui.
 */
public record PlafondsVersement(
        BigDecimal tiroirEspeces, BigDecimal tiroirTpe, BigDecimal compteEspeces, BigDecimal compteTpe) {

    public PlafondsVersement {
        Objects.requireNonNull(tiroirEspeces);
        Objects.requireNonNull(tiroirTpe);
        Objects.requireNonNull(compteEspeces);
        Objects.requireNonNull(compteTpe);
    }

    /** Sans caisse ouverte : tout le dû est sur le caissier. */
    public static PlafondsVersement horsSession(DuParMode du) {
        return new PlafondsVersement(
                BigDecimal.ZERO, BigDecimal.ZERO, du.especes().max(BigDecimal.ZERO), du.tpe());
    }

    /**
     * Caisse ouverte. {@code duAvecSession} : dû si la session était clôturée maintenant (ventes comprises) ;
     * {@code attenduEspeces} et {@code attenduTpe} : contenu attendu du tiroir.
     */
    public static PlafondsVersement enSession(
            DuParMode duAvecSession, BigDecimal attenduEspeces, BigDecimal attenduTpe) {
        BigDecimal tiroirEspeces = attenduEspeces.max(BigDecimal.ZERO);
        BigDecimal tiroirTpe = attenduTpe.max(BigDecimal.ZERO);
        return new PlafondsVersement(
                tiroirEspeces,
                tiroirTpe,
                duAvecSession.especes().subtract(tiroirEspeces).max(BigDecimal.ZERO),
                duAvecSession.tpe().subtract(tiroirTpe).max(BigDecimal.ZERO));
    }

    /** Chaque part du versement reste dans son plafond. */
    public void verifier(
            BigDecimal tiroirEspeces, BigDecimal tiroirTpe, BigDecimal compteEspeces, BigDecimal compteTpe) {
        verifier(tiroirEspeces, this.tiroirEspeces, "en espèces depuis le tiroir", "le tiroir en contient");
        verifier(tiroirTpe, this.tiroirTpe, "en tickets TPE depuis le tiroir", "le tiroir en contient");
        verifier(compteEspeces, this.compteEspeces, "en espèces depuis votre compte", "hors tiroir, vous devez");
        verifier(compteTpe, this.compteTpe, "en tickets TPE depuis votre compte", "hors tiroir, vous devez");
    }

    private static void verifier(BigDecimal montant, BigDecimal plafond, String quoi, String contexte) {
        if (montant.compareTo(plafond) > 0) {
            throw new RegleMetierException("Le versement " + quoi + " dépasse le plafond : " + contexte + " "
                    + plafond.toPlainString() + " DH.");
        }
    }
}
