package ma.invento.pos.parametre.domain;

/** Clés des paramètres modifiables par l'admin (table {@code parametre}). */
public enum CleParametre {
    PLAFOND_REMISE_CAISSIER("plafond_remise_caissier"),
    PLAFOND_REMISE_RESPONSABLE("plafond_remise_responsable"),
    BOUTIQUE_NOM("boutique_nom"),
    BOUTIQUE_ADRESSE("boutique_adresse"),
    FUSEAU_HORAIRE("fuseau_horaire");

    private final String cle;

    CleParametre(String cle) {
        this.cle = cle;
    }

    public String cle() {
        return cle;
    }
}
