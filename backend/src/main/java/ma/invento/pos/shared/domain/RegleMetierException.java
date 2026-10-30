package ma.invento.pos.shared.domain;

/** Règle métier violée par une demande par ailleurs bien formée (HTTP 422). */
public class RegleMetierException extends RuntimeException {

    public RegleMetierException(String message) {
        super(message);
    }
}
