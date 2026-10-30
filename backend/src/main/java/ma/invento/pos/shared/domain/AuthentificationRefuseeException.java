package ma.invento.pos.shared.domain;

/** Identifiants, jeton ou code refusés (HTTP 401). */
public class AuthentificationRefuseeException extends RuntimeException {

    public AuthentificationRefuseeException(String message) {
        super(message);
    }
}
