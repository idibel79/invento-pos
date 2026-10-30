package ma.invento.pos.shared.domain;

/** Ressource demandée absente (HTTP 404). */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
