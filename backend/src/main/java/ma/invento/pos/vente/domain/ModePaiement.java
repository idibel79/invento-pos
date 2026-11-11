package ma.invento.pos.vente.domain;

public enum ModePaiement {
    ESPECES,
    CARTE,
    /** Vente à crédit : la totalité du ticket s'ajoute à la dette du client, aucun argent n'entre. */
    CREDIT
}
