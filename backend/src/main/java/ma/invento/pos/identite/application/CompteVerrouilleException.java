package ma.invento.pos.identite.application;

import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.shared.domain.OperationInterditeException;

/** Compte verrouillé après trop d'essais ratés (HTTP 403). */
public class CompteVerrouilleException extends OperationInterditeException {

    /**
     * Message de l'écran de connexion : identique pour tous les comptes, y compris les identifiants
     * inconnus, pour ne révéler ni l'existence ni le rôle d'un compte.
     */
    static final String MESSAGE_CONNEXION = "Compte verrouillé après " + Utilisateur.ESSAIS_MAX
            + " essais ratés. Demandez un nouveau mot de passe à l'administrateur.";

    public CompteVerrouilleException() {
        this("Compte verrouillé après " + Utilisateur.ESSAIS_MAX
                + " essais ratés. Demandez à l'administrateur de le déverrouiller.");
    }

    public CompteVerrouilleException(String message) {
        super(message);
    }
}
