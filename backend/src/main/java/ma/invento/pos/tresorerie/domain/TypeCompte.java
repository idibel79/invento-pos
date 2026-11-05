package ma.invento.pos.tresorerie.domain;

public enum TypeCompte {
    /** Caisse principale de la boutique, tenue par l'admin : un solde unique (espèces et tickets TPE). */
    PRINCIPALE,
    /** Compte d'un utilisateur : ce qu'il doit à la caisse principale. */
    CAISSIER
}
