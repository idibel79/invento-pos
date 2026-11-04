package ma.invento.pos.shared.domain;

/**
 * Règle métier violée par une demande par ailleurs bien formée (HTTP 422). Le code facultatif
 * permet à l'écran de réagir à un cas précis sans analyser le message.
 */
public class RegleMetierException extends RuntimeException {

    private final String code;

    public RegleMetierException(String message) {
        this(message, null);
    }

    public RegleMetierException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
