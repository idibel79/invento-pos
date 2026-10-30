package ma.invento.pos.vente.domain;

import java.math.BigDecimal;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.parametre.domain.PlafondsRemise;

/**
 * Qui peut accorder une remise (cadrage § 1) : jusqu'au plafond caissier, tout vendeur ; jusqu'au
 * plafond responsable, un responsable (PIN) ; au-delà, l'admin.
 */
public enum NiveauAutorisation {
    AUCUNE,
    RESPONSABLE,
    ADMIN;

    public static NiveauAutorisation requis(BigDecimal tauxRemise, PlafondsRemise plafonds) {
        if (tauxRemise.compareTo(plafonds.caissier()) <= 0) return AUCUNE;
        if (tauxRemise.compareTo(plafonds.responsable()) <= 0) return RESPONSABLE;
        return ADMIN;
    }

    public boolean estCouvertPar(Role role) {
        return switch (this) {
            case AUCUNE -> true;
            case RESPONSABLE -> role == Role.RESPONSABLE || role == Role.ADMIN;
            case ADMIN -> role == Role.ADMIN;
        };
    }
}
