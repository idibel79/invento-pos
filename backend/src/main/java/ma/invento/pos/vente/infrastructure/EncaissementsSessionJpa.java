package ma.invento.pos.vente.infrastructure;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import ma.invento.pos.caisse.application.EncaissementsSession;
import ma.invento.pos.vente.domain.ModePaiement;
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

    @Override
    public VentesSession ventes(long sessionId) {
        return ventesParSession(List.of(sessionId)).getOrDefault(sessionId, VentesSession.AUCUNE);
    }

    @Override
    public Map<Long, VentesSession> ventesParSession(Collection<Long> sessionIds) {
        if (sessionIds.isEmpty()) return Map.of();
        Map<Long, VentesSession> parSession = new HashMap<>();
        for (PaiementsSession p : ventes.sommesParMode(sessionIds)) {
            VentesSession actuel = parSession.getOrDefault(p.sessionId(), VentesSession.AUCUNE);
            parSession.put(
                    p.sessionId(),
                    p.mode() == ModePaiement.ESPECES
                            ? new VentesSession(actuel.especes().add(p.montant()), actuel.carte())
                            : new VentesSession(actuel.especes(), actuel.carte().add(p.montant())));
        }
        return parSession;
    }
}
