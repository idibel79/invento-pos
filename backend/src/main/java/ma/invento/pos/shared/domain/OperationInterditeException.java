package ma.invento.pos.shared.domain;

/** Utilisateur identifié mais opération interdite dans son état actuel (HTTP 403). */
public class OperationInterditeException extends RuntimeException {

    public OperationInterditeException(String message) {
        super(message);
    }
}
