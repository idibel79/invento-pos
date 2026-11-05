package ma.invento.pos.shared.domain;

import java.math.BigDecimal;

/**
 * Ce qu'une autorisation de remise couvre : une remise précise, sur un article ou sur le ticket.
 * Inscrit dans le jeton signé à la saisie du PIN et recalculé depuis le panier à la vente : une
 * autorisation ne vaut que pour la remise qu'elle a accordée.
 */
public final class ObjetAutorisation {

    private ObjetAutorisation() {}

    /**
     * @param produitId article remisé, ou null pour une remise sur le ticket
     * @param type type de remise (POURCENT ou MONTANT)
     */
    public static String remise(Long produitId, String type, BigDecimal valeur) {
        String cible = produitId == null ? "TICKET" : "LIGNE:" + produitId;
        return cible + "|" + type + ":" + valeur.stripTrailingZeros().toPlainString();
    }
}
