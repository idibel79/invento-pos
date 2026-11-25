package ma.invento.pos.identite.application;

import java.time.Duration;
import java.time.Instant;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.shared.domain.OperationInterditeException;

/** Compte verrouillé après trop d'essais ratés (HTTP 403). */
public class CompteVerrouilleException extends OperationInterditeException {

    /**
     * Refus de l'écran de connexion pendant le verrouillage temporaire : même message pour tous les
     * comptes, y compris les identifiants inconnus, pour ne révéler ni l'existence ni le rôle d'un compte.
     */
    static CompteVerrouilleException connexion(Instant fin, Instant maintenant) {
        long minutes = Math.max(1, (Duration.between(maintenant, fin).getSeconds() + 59) / 60);
        return new CompteVerrouilleException("Compte verrouillé après " + Utilisateur.ESSAIS_MAX
                + " essais ratés. Réessayez dans " + minutes + " min ou demandez à l'administrateur de le débloquer.");
    }

    public CompteVerrouilleException() {
        this("Compte verrouillé après " + Utilisateur.ESSAIS_MAX
                + " essais ratés. Demandez à l'administrateur de le déverrouiller.");
    }

    public CompteVerrouilleException(String message) {
        super(message);
    }
}
