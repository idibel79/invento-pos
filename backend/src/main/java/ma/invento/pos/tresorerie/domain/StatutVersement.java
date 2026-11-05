package ma.invento.pos.tresorerie.domain;

import ma.invento.pos.shared.domain.ConflitException;

public enum StatutVersement {
    /** Débité chez l'émetteur, pas encore confirmé par le récepteur. */
    EN_TRANSIT,
    /** Le récepteur a confirmé avoir reçu l'argent : crédité chez lui. */
    VALIDE,
    /** Le récepteur l'a refusé (avec un motif) : l'argent est revenu à l'émetteur. */
    REFUSE,
    /** L'émetteur l'a annulé avant sa validation : l'argent lui est revenu. */
    ANNULE;

    /** Seul un versement en transit peut être validé, refusé ou annulé. */
    public void verifierEnTransit() {
        if (this != EN_TRANSIT) {
            throw new ConflitException("Ce versement a déjà été traité (" + libelle() + ").");
        }
    }

    public String libelle() {
        return switch (this) {
            case EN_TRANSIT -> "en transit";
            case VALIDE -> "validé";
            case REFUSE -> "refusé";
            case ANNULE -> "annulé";
        };
    }
}
