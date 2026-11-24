package ma.invento.pos.identite.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import ma.invento.pos.identite.infrastructure.EssaisConnexionJdbc;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supprime chaque nuit les compteurs d'essais devenus inutiles : fenêtres IP de plus d'un jour et
 * identifiants inconnus sans essai depuis 30 jours.
 */
@Component
@ConditionalOnWebApplication
class PurgeEssaisConnexion {

    private static final Logger log = LoggerFactory.getLogger(PurgeEssaisConnexion.class);
    private static final Duration CONSERVATION_FENETRES = Duration.ofDays(1);
    private static final Duration CONSERVATION_INCONNUS = Duration.ofDays(30);

    private final EssaisConnexionJdbc essais;
    private final Clock clock;

    PurgeEssaisConnexion(EssaisConnexionJdbc essais, Clock clock) {
        this.essais = essais;
        this.clock = clock;
    }

    @Scheduled(cron = "0 17 3 * * *")
    @SchedulerLock(name = "purge-essais-connexion", lockAtMostFor = "PT5M")
    @Transactional
    public void purger() {
        Instant maintenant = Instant.now(clock);
        int supprimes = essais.purger(maintenant.minus(CONSERVATION_FENETRES), maintenant.minus(CONSERVATION_INCONNUS));
        if (supprimes > 0) log.info("{} compteur(s) d'essais de connexion purgé(s)", supprimes);
    }
}
