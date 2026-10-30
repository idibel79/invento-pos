package ma.invento.pos.identite.application;

import ma.invento.pos.shared.domain.RegleMetierException;

/**
 * PIN refusé lors d'une autorisation sur la caisse (HTTP 422, pas 401 : la session du caissier
 * reste valide).
 */
public class PinIncorrectException extends RegleMetierException {

    public PinIncorrectException(String message) {
        super(message);
    }
}
