package ma.invento.pos.identite.application;

import java.time.Clock;
import java.time.Instant;
import ma.invento.pos.identite.infrastructure.JetonsRemiseUtilisesJdbc;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Supprime chaque nuit les jetons de remise utilisés et échus : présentés à nouveau, ils seraient refusés. */
@Component
@ConditionalOnWebApplication
class PurgeJetonsRemise {

    private static final Logger log = LoggerFactory.getLogger(PurgeJetonsRemise.class);

    private final JetonsRemiseUtilisesJdbc jetons;
    private final Clock clock;

    PurgeJetonsRemise(JetonsRemiseUtilisesJdbc jetons, Clock clock) {
        this.jetons = jetons;
        this.clock = clock;
    }

    @Scheduled(cron = "0 27 3 * * *")
    @SchedulerLock(name = "purge-jetons-remise", lockAtMostFor = "PT5M")
    @Transactional
    public void purger() {
        int supprimes = jetons.purger(Instant.now(clock));
        if (supprimes > 0) log.info("{} jeton(s) de remise échu(s) purgé(s)", supprimes);
    }
}
