package ma.invento.pos.parametre.domain;

/** Clés des paramètres modifiables par l'admin (table {@code parametre}). */
public enum CleParametre {
    BOUTIQUE_NOM("boutique_nom"),
    BOUTIQUE_ADRESSE("boutique_adresse"),
    BOUTIQUE_TELEPHONE("boutique_telephone"),
    BOUTIQUE_EMAIL("boutique_email"),
    BOUTIQUE_ICE("boutique_ice"),
    BOUTIQUE_IF("boutique_if"),
    BOUTIQUE_RC("boutique_rc"),
    BOUTIQUE_PATENTE("boutique_patente"),
    TICKET_MESSAGE("ticket_message"),
    FUSEAU_HORAIRE("fuseau_horaire");

    private final String cle;

    CleParametre(String cle) {
        this.cle = cle;
    }

    public String cle() {
        return cle;
    }
}
