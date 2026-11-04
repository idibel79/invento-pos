package ma.invento.pos.vente.infrastructure;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import ma.invento.pos.caisse.application.EncaissementsSession;
import org.springframework.stereotype.Component;

@Component
class EncaissementsSessionJpa implements EncaissementsSession {

    private final VenteRepository ventes;

    EncaissementsSessionJpa(VenteRepository ventes) {
        this.ventes = ventes;
    }

    @Override
    public BigDecimal especesEncaissees(long sessionId) {
        return ventes.sommeEspeces(sessionId);
    }

    @Override
    public Map<Long, BigDecimal> especesEncaisseesParSession(Collection<Long> sessionIds) {
        if (sessionIds.isEmpty()) return Map.of();
        return ventes.sommesEspeces(sessionIds).stream()
                .collect(Collectors.toMap(EspecesSession::sessionId, EspecesSession::montant));
    }
}
