package ma.invento.pos.importation.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Colonnes reconnues dans les exports Sobrus ({@code "Code barre 1"}) et Invento
 * ({@code "code_barre_1"}) : les en-têtes sont comparés après normalisation (casse, accents,
 * espaces), comme {@code ExcelColumnMapper} dans Invento.
 */
public enum Colonne {
    PID("pid"),
    PRODUIT("produit"),
    CATEGORIE("categorie"),
    TVA("tva"),
    PPV("ppv"),
    PPH("pph"),
    ZONE("zone"),
    STOCK("stock"),
    DATE_PEREMPTION("date_de_peremption"),
    STOCK_MIN("stock_min"),
    STOCK_MAX("stock_max"),
    CODE_BARRE_1("code_barre_1"),
    CODE_BARRE_2("code_barre_2"),
    // Colonnes ajoutées par l'export Invento.
    NUMERO_INVENTAIRE("numero_inventaire"),
    DATE_INVENTAIRE("date_inventaire"),
    QUANTITE_REELLE("quantite_reelle");

    private final String cle;

    Colonne(String cle) {
        this.cle = cle;
    }

    public String cle() {
        return cle;
    }

    /** {@code "Date de pÃ©remption"} réparé puis normalisé donne {@code "date_de_peremption"}. */
    public static String normaliser(String entete) {
        if (entete == null) return "";
        String sansAccents =
                Normalizer.normalize(entete.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
