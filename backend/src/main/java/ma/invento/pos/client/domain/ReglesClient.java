package ma.invento.pos.client.domain;

import java.math.BigDecimal;
import ma.invento.pos.shared.domain.RegleMetierException;

/** Règles de l'annuaire des clients et de leur dette, sans dépendance technique. */
public final class ReglesClient {

    public static final int NOM_MAX = 120;
    public static final int TELEPHONE_MAX = 30;
    public static final int NOTE_MAX = 300;
    public static final int MOTIF_MAX = 300;

    private ReglesClient() {}

    /** Nom nettoyé, obligatoire. */
    public static String nom(String nom) {
        String propre = nom == null ? "" : nom.strip().replaceAll("\\s+", " ");
        if (propre.isEmpty()) throw new RegleMetierException("Le nom du client est obligatoire.");
        if (propre.length() > NOM_MAX) {
            throw new RegleMetierException("Le nom ne doit pas dépasser " + NOM_MAX + " caractères.");
        }
        return propre;
    }

    /** Texte facultatif nettoyé : null s'il est vide. */
    public static String facultatif(String texte, int max, String libelle) {
        String propre = texte == null ? "" : texte.strip();
        if (propre.isEmpty()) return null;
        if (propre.length() > max) {
            throw new RegleMetierException(libelle + " ne doit pas dépasser " + max + " caractères.");
        }
        return propre;
    }

    /**
     * Remboursement : parts en espèces et par TPE positives ou nulles, au centime, dont le total est
     * strictement positif. Il peut dépasser la dette : le surplus devient une avance du client.
     *
     * @return le total remboursé
     */
    public static BigDecimal remboursement(BigDecimal especes, BigDecimal tpe) {
        BigDecimal e = especes == null ? BigDecimal.ZERO : especes;
        BigDecimal t = tpe == null ? BigDecimal.ZERO : tpe;
        if (e.signum() < 0 || t.signum() < 0) {
            throw new RegleMetierException("Les montants ne peuvent pas être négatifs.");
        }
        if (e.stripTrailingZeros().scale() > 2 || t.stripTrailingZeros().scale() > 2) {
            throw new RegleMetierException("Le montant se saisit au centime près.");
        }
        BigDecimal total = e.add(t);
        if (total.signum() <= 0) throw new RegleMetierException("Indiquez le montant remboursé.");
        return total;
    }

    /** Plafond de crédit facultatif : absent, pas de limite ; sinon strictement positif, au centime. */
    public static BigDecimal plafond(BigDecimal plafond) {
        if (plafond == null) return null;
        if (plafond.signum() <= 0) throw new RegleMetierException("Le plafond doit être supérieur à 0.");
        if (plafond.stripTrailingZeros().scale() > 2) {
            throw new RegleMetierException("Le plafond se saisit au centime près.");
        }
        return plafond;
    }

    /** Une vente à crédit porte sur un montant strictement positif. */
    public static void verifierVenteACredit(BigDecimal total) {
        if (total == null || total.signum() <= 0) {
            throw new RegleMetierException("Une vente à 0 DH ne peut pas être payée à crédit.");
        }
    }
}
