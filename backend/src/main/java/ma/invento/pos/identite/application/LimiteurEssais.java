package ma.invento.pos.identite.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import ma.invento.pos.identite.config.LimitesEssaisProperties;
import ma.invento.pos.identite.domain.GenerateurSecrets;
import ma.invento.pos.identite.infrastructure.EssaisConnexionJdbc;
import ma.invento.pos.shared.domain.TropDEssaisException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plafonne les essais ratés par adresse IP, par fenêtres de temps fixes, avec les compteurs en base
 * pour que toutes les instances de l'API partagent la même limite. L'adresse n'est conservée que
 * sous forme d'empreinte.
 */
@Service
public class LimiteurEssais {

    private final EssaisConnexionJdbc essais;
    private final LimitesEssaisProperties limites;
    private final Clock clock;

    public LimiteurEssais(EssaisConnexionJdbc essais, LimitesEssaisProperties limites, Clock clock) {
        this.essais = essais;
        this.limites = limites;
        this.clock = clock;
    }

    public Limite connexion() {
        return limites.connexion();
    }

    public Limite secours() {
        return limites.secours();
    }

    /** Refuse la requête si l'adresse a déjà atteint le plafond dans la fenêtre en cours. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void verifier(Limite limite, String adresseIp) {
        Instant maintenant = Instant.now(clock);
        Instant fenetre = debutFenetre(limite, maintenant);
        if (essais.echecs(cle(limite, adresseIp), fenetre) >= limite.echecs()) {
            throw new TropDEssaisException(Duration.between(maintenant, fenetre.plus(limite.fenetre())));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enregistrerEchec(Limite limite, String adresseIp) {
        essais.ajouterEchec(cle(limite, adresseIp), debutFenetre(limite, Instant.now(clock)));
    }

    private static String cle(Limite limite, String adresseIp) {
        return limite.nom() + ":" + GenerateurSecrets.empreinte(adresseIp);
    }

    private static Instant debutFenetre(Limite limite, Instant maintenant) {
        long duree = limite.fenetre().getSeconds();
        return Instant.ofEpochSecond(maintenant.getEpochSecond() / duree * duree);
    }

    /** Plafond d'échecs pour un type d'essai, sur une fenêtre de temps. */
    public record Limite(String nom, int echecs, Duration fenetre) {}
}
