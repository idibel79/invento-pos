package ma.invento.pos.identite.application;

import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.shared.domain.OperationInterditeException;

/** Mot de passe correct mais compte verrouillé après trop d'essais ratés (HTTP 403). */
public class CompteVerrouilleException extends OperationInterditeException {

    public CompteVerrouilleException() {
        super("Compte verrouillé après " + Utilisateur.ESSAIS_MAX
                + " essais ratés. Demandez à l'administrateur de le déverrouiller.");
    }
}
