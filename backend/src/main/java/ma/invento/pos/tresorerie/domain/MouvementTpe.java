package ma.invento.pos.tresorerie.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Mouvement qui fait varier les tickets TPE détenus par un utilisateur : encaissement par TPE (vente par carte,
 * dette remboursée par TPE) ou versement à la caisse principale. {@code montant} d'un versement : sa part TPE,
 * ou son total s'il n'a pas de répartition (versements antérieurs à la saisie espèces/TPE, versement du
 * comptage par l'admin).
 */
public record MouvementTpe(Nature nature, BigDecimal montant) {

    public enum Nature {
        ENCAISSEMENT,
        VERSEMENT
    }

    public MouvementTpe {
        Objects.requireNonNull(nature);
        Objects.requireNonNull(montant);
    }

    /**
     * Tickets TPE encaissés et pas encore versés, mouvements pris dans l'ordre chronologique. Un versement sans
     * répartition emporte d'abord les tickets TPE détenus (ils sont remis avec l'argent), le reste en espèces.
     */
    public static BigDecimal nonVerses(List<MouvementTpe> mouvements) {
        BigDecimal tpe = BigDecimal.ZERO;
        for (MouvementTpe m : mouvements) {
            tpe = m.nature() == Nature.ENCAISSEMENT
                    ? tpe.add(m.montant())
                    : tpe.subtract(m.montant()).max(BigDecimal.ZERO);
        }
        return tpe;
    }
}
