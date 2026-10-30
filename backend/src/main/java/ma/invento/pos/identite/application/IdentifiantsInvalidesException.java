package ma.invento.pos.identite.application;

import ma.invento.pos.shared.domain.AuthentificationRefuseeException;

/** Identifiants, jeton ou code refusés (HTTP 401), sans dire lequel pour ne rien divulguer. */
public class IdentifiantsInvalidesException extends AuthentificationRefuseeException {

    public IdentifiantsInvalidesException(String message) {
        super(message);
    }
}
