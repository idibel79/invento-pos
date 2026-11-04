package ma.invento.pos.vente.domain;

public enum StatutVente {
    /** Client parti chercher de quoi payer : stock réservé 5 minutes au plus. */
    EN_ATTENTE,
    /** Vente en attente reprise dans le ticket : plus de stock réservé, encaissée ou abandonnée ensuite. */
    REPRISE,
    VALIDEE,
    ANNULEE
}
