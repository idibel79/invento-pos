package ma.invento.pos.vente.domain;

import java.math.BigDecimal;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.SeuilsRemise;

/**
 * Qui peut accorder une remise : un caissier jamais ; un responsable dans les seuils que l'admin lui
 * a fixés (par ligne ou sur le ticket) ; l'admin sans limite. Une remise que le vendeur ne couvre
 * pas demande le PIN d'un responsable qui la couvre, sinon celui de l'admin.
 */
public enum NiveauAutorisation {
    AUCUNE,
    RESPONSABLE,
    ADMIN;

    public static boolean aUneRemise(PanierCalcule calcul) {
        return calcul.totalRemises().signum() > 0;
    }

    /**
     * Ce rôle, avec ces seuils, peut-il accorder une remise de ce taux (en %) sur le ticket ou sur un
     * article ?
     */
    public static boolean couvre(Role role, SeuilsRemise seuils, BigDecimal taux, boolean remiseTicket) {
        if (taux.signum() == 0) return true;
        return switch (role) {
            case ADMIN -> true;
            case CAISSIER -> false;
            case RESPONSABLE -> seuils.couvre(taux, remiseTicket);
        };
    }

    /** Ce rôle, avec ces seuils, peut-il accorder la remise de ce panier ? */
    public static boolean couvre(Role role, SeuilsRemise seuils, PanierCalcule calcul) {
        if (!aUneRemise(calcul)) return true;
        return couvre(
                role, seuils, calcul.tauxRemiseMax(), calcul.remiseTicket().signum() > 0);
    }

    /** Autorisation à demander quand le vendeur ne couvre pas lui-même la remise. */
    public static NiveauAutorisation requis(boolean vendeurCouvre, boolean unResponsableCouvre) {
        if (vendeurCouvre) return AUCUNE;
        return unResponsableCouvre ? RESPONSABLE : ADMIN;
    }
}
