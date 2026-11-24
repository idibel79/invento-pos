package ma.invento.pos.identite.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Forme unique d'un identifiant de connexion : minuscules, sans accents, espaces remplacés par un
 * point (« Hélène Ait Ali » donne {@code helene.ait.ali}). La même règle sert à proposer
 * l'identifiant d'un nouveau compte depuis son prénom et son nom, et à lire la saisie de l'écran de
 * connexion : taper son prénom et son nom retrouve donc son compte.
 */
public final class IdentifiantConnexion {

    /** Longueur de la colonne {@code utilisateur.login}. */
    public static final int LONGUEUR_MAX = 60;

    private IdentifiantConnexion() {}

    /** Forme normalisée d'une saisie ; vide si la saisie ne contient aucun caractère utilisable. */
    public static String normaliser(String saisie) {
        if (saisie == null) return "";
        String sansAccents = Normalizer.normalize(saisie, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String forme = sansAccents
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[\\s.]+", ".")
                .replaceAll("[^a-z0-9._-]", "")
                .replaceAll("\\.{2,}", ".")
                .replaceAll("^[._-]+|[._-]+$", "");
        return forme.length() > LONGUEUR_MAX ? forme.substring(0, LONGUEUR_MAX) : forme;
    }

    /**
     * Identifiant libre proposé pour « prénom nom » : la forme normalisée, suivie d'un chiffre si
     * elle est déjà prise ({@code mohamed.alaoui2}). Vide si le prénom et le nom n'en donnent pas.
     */
    public static String proposer(String prenom, String nom, Predicate<String> dejaPris) {
        String base = normaliser((prenom == null ? "" : prenom) + " " + (nom == null ? "" : nom));
        if (base.isEmpty() || !dejaPris.test(base)) return base;
        for (int n = 2; ; n++) {
            String suffixe = Integer.toString(n);
            String candidat = base.substring(0, Math.min(base.length(), LONGUEUR_MAX - suffixe.length())) + suffixe;
            if (!dejaPris.test(candidat)) return candidat;
        }
    }
}
