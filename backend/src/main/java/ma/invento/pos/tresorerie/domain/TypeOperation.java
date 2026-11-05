package ma.invento.pos.tresorerie.domain;

/** Mouvement de solde inscrit au journal de la caisse principale. */
public enum TypeOperation {
    /** Apport d'argent dans la caisse principale. */
    ALIMENTATION,
    /** Sortie d'argent de la caisse principale (dépôt en banque…). */
    RETRAIT,
    /** Envoi d'un versement : l'émetteur est débité. */
    VERSEMENT_ENVOYE,
    /** Versement confirmé par le récepteur : il est crédité. */
    VERSEMENT_RECU,
    /** Versement refusé par le récepteur : l'émetteur est recrédité. */
    VERSEMENT_REFUSE,
    /** Versement annulé par l'émetteur : il est recrédité. */
    VERSEMENT_ANNULE,
    /** Passage en perte par l'admin : le solde du caissier baisse, sans effet sur la caisse principale. */
    AJUSTEMENT,
    /** Clôture d'une session : les ventes et l'excédent du tiroir s'ajoutent au solde du caissier. */
    CLOTURE_SESSION
}
