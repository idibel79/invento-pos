package ma.invento.pos.vente.domain;

public enum StatutVente {
    /** Client parti chercher de quoi payer : stock réservé 5 minutes au plus. */
    EN_ATTENTE,
    VALIDEE,
    ANNULEE
}
