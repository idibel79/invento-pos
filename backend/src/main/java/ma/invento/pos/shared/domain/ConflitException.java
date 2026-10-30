package ma.invento.pos.shared.domain;

/** Demande en conflit avec l'état actuel des données, ex. valeur déjà utilisée (HTTP 409). */
public class ConflitException extends RuntimeException {

    public ConflitException(String message) {
        super(message);
    }
}
