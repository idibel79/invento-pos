package ma.invento.pos.client.domain;

/** Ligne du relevé d'un client. */
public enum TypeMouvementClient {
    /** Vente payée à crédit : la dette augmente du total du ticket. */
    VENTE_CREDIT,
    /** Le client rembourse tout ou partie de sa dette, en espèces et/ou par TPE. */
    REMBOURSEMENT,
    /** L'admin annule un remboursement saisi par erreur : la dette remonte du même montant. */
    ANNULATION_REMBOURSEMENT
}
