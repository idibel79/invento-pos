package ma.invento.pos.vente.infrastructure;

/** Recherche des ventes par nom de client dans les journaux : partie du nom, sans tenir compte de la casse. */
final class RechercheClient {

    /** Condition SQL sur la colonne du nom de client, à n'ajouter que si {@link #motif} n'est pas null. */
    static String condition(String colonne) {
        return " and lower(" + colonne + ") like :client";
    }

    /** Motif {@code like} échappé ; null si la saisie est vide (pas de filtre). */
    static String motif(String saisie) {
        if (saisie == null || saisie.isBlank()) return null;
        return "%"
                + saisie.strip()
                        .toLowerCase()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_")
                + "%";
    }

    private RechercheClient() {}
}
