package ma.invento.pos.vente.application;

import java.time.Clock;
import java.time.Instant;
import ma.invento.pos.vente.infrastructure.VenteRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Annule les ventes en attente depuis plus de 5 minutes et libère leur stock. ShedLock garantit
 * qu'une seule instance de l'API exécute la tâche à la fois ; chaque vente est traitée dans sa
 * propre transaction.
 */
@Component
public class VentesEnAttenteExpirees {

    private static final Logger log = LoggerFactory.getLogger(VentesEnAttenteExpirees.class);

    private final VenteRepository ventes;
    private final VenteService venteService;
    private final Clock clock;

    VentesEnAttenteExpirees(VenteRepository ventes, VenteService venteService, Clock clock) {
        this.ventes = ventes;
        this.venteService = venteService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${pos.vente.verification-attentes:PT15S}")
    @SchedulerLock(name = "ventes-en-attente-expirees", lockAtMostFor = "PT2M")
    public void annuler() {
        int annulees = 0;
        for (long id : ventes.findIdsAttentesExpirees(Instant.now(clock))) {
            try {
                if (venteService.annulerSiExpiree(id)) annulees++;
            } catch (RuntimeException e) {
                log.error("Échec de l'annulation de la vente en attente {}", id, e);
            }
        }
        if (annulees > 0) log.info("{} vente(s) en attente expirée(s) annulée(s)", annulees);
    }
}
