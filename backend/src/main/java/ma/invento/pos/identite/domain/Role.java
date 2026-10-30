package ma.invento.pos.identite.domain;

/** Rôles du cadrage : ADMIN hérite des droits de RESPONSABLE, qui hérite de ceux de CAISSIER. */
public enum Role {
    CAISSIER,
    RESPONSABLE,
    ADMIN
}
