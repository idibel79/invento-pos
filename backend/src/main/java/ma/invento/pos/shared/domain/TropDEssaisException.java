package ma.invento.pos.shared.domain;

import java.time.Duration;

/** Trop d'essais ratés depuis la même adresse (HTTP 429, avec l'attente avant de réessayer). */
public class TropDEssaisException extends RuntimeException {

    private final Duration attente;

    public TropDEssaisException(Duration attente) {
        super("Trop d'essais ratés depuis cet appareil. Réessayez dans " + minutes(attente) + " min.");
        this.attente = attente;
    }

    public Duration getAttente() {
        return attente;
    }

    private static long minutes(Duration attente) {
        return Math.max(1, (attente.getSeconds() + 59) / 60);
    }
}
