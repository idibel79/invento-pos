package ma.invento.pos.identite.config;

import java.time.Duration;
import ma.invento.pos.identite.application.LimiteurEssais.Limite;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Plafonds d'essais ratés par adresse IP, contre la force brute : au-delà, la requête est refusée
 * jusqu'à la fenêtre suivante.
 *
 * @param connexionEchecs connexions ratées tolérées (mauvais identifiant ou mot de passe)
 * @param connexionFenetre durée de la fenêtre des connexions
 * @param secoursEchecs codes de secours ratés tolérés
 * @param secoursFenetre durée de la fenêtre des codes de secours
 */
@ConfigurationProperties("pos.security.limites")
public record LimitesEssaisProperties(
        @DefaultValue("20") int connexionEchecs,
        @DefaultValue("15m") Duration connexionFenetre,
        @DefaultValue("5") int secoursEchecs,
        @DefaultValue("1h") Duration secoursFenetre) {

    public Limite connexion() {
        return new Limite("CONNEXION", connexionEchecs, connexionFenetre);
    }

    public Limite secours() {
        return new Limite("SECOURS", secoursEchecs, secoursFenetre);
    }
}
