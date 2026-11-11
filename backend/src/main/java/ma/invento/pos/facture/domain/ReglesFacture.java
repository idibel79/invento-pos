package ma.invento.pos.facture.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import ma.invento.pos.shared.domain.RegleMetierException;

/** Numérotation des factures et montant en toutes lettres, sans dépendance technique. */
public final class ReglesFacture {

    private static final String[] UNITES = {
        "zéro",
        "un",
        "deux",
        "trois",
        "quatre",
        "cinq",
        "six",
        "sept",
        "huit",
        "neuf",
        "dix",
        "onze",
        "douze",
        "treize",
        "quatorze",
        "quinze",
        "seize",
        "dix-sept",
        "dix-huit",
        "dix-neuf"
    };
    private static final String[] DIZAINES = {
        "", "", "vingt", "trente", "quarante", "cinquante", "soixante", "soixante", "quatre-vingt", "quatre-vingt"
    };
    private static final long MAX = 999_999_999_999L;

    private ReglesFacture() {}

    /** Numéro lisible et trié : {@code FA-2026-00042}, la séquence repart à 1 chaque année. */
    public static String numero(int annee, int sequence) {
        if (sequence < 1) throw new IllegalArgumentException("Séquence de facture invalide : " + sequence);
        return "FA-%d-%05d".formatted(annee, sequence);
    }

    /**
     * Montant en toutes lettres pour la mention « Arrêtée la présente facture à la somme de » :
     * {@code 1260.50} donne « mille deux cent soixante dirhams et cinquante centimes ».
     */
    public static String montantEnLettres(BigDecimal montant) {
        if (montant == null || montant.signum() < 0) {
            throw new RegleMetierException("Le montant d'une facture ne peut pas être négatif.");
        }
        BigDecimal arrondi = montant.setScale(2, RoundingMode.HALF_UP);
        long dirhams = arrondi.longValue();
        if (dirhams > MAX) throw new RegleMetierException("Montant trop élevé pour une facture.");
        int centimes = arrondi.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();
        String texteDirhams = nombre(dirhams) + (dirhams > 1 ? " dirhams" : " dirham");
        if (centimes == 0) return texteDirhams;
        String texteCentimes = nombre(centimes) + (centimes > 1 ? " centimes" : " centime");
        return dirhams == 0 ? texteCentimes : texteDirhams + " et " + texteCentimes;
    }

    /** Nombre entier en lettres, orthographe traditionnelle (vingt et un, quatre-vingts, deux cents). */
    static String nombre(long n) {
        if (n == 0) return UNITES[0];
        StringBuilder texte = new StringBuilder();
        long milliards = n / 1_000_000_000L;
        int millions = (int) (n / 1_000_000L % 1000);
        int milliers = (int) (n / 1000 % 1000);
        int reste = (int) (n % 1000);
        if (milliards > 0) {
            ajouter(texte, groupe((int) milliards, false) + (milliards > 1 ? " milliards" : " milliard"));
        }
        if (millions > 0) ajouter(texte, groupe(millions, false) + (millions > 1 ? " millions" : " million"));
        if (milliers > 0) ajouter(texte, milliers == 1 ? "mille" : groupe(milliers, true) + " mille");
        if (reste > 0) ajouter(texte, groupe(reste, false));
        return texte.toString();
    }

    private static void ajouter(StringBuilder texte, String mot) {
        if (!texte.isEmpty()) texte.append(' ');
        texte.append(mot);
    }

    /**
     * De 1 à 999. {@code devantMille} : « cent » et « quatre-vingt » restent invariables devant « mille »
     * (deux cent mille, quatre-vingt mille).
     */
    private static String groupe(int n, boolean devantMille) {
        int centaines = n / 100;
        int reste = n % 100;
        StringBuilder texte = new StringBuilder();
        if (centaines > 0) {
            if (centaines > 1) texte.append(UNITES[centaines]).append(' ');
            texte.append("cent");
            if (centaines > 1 && reste == 0 && !devantMille) texte.append('s');
        }
        if (reste > 0) ajouter(texte, dizaines(reste, devantMille));
        return texte.toString();
    }

    private static String dizaines(int n, boolean devantMille) {
        if (n < 20) return UNITES[n];
        int d = n / 10;
        int u = n % 10;
        if (d == 7 || d == 9) {
            // soixante-dix à soixante-dix-neuf (soixante et onze), quatre-vingt-dix à quatre-vingt-dix-neuf
            return DIZAINES[d] + (d == 7 && u == 1 ? " et " : "-") + UNITES[10 + u];
        }
        if (d == 8) {
            if (u > 0) return "quatre-vingt-" + UNITES[u];
            return devantMille ? "quatre-vingt" : "quatre-vingts";
        }
        if (u == 0) return DIZAINES[d];
        return DIZAINES[d] + (u == 1 ? " et un" : "-" + UNITES[u]);
    }
}
