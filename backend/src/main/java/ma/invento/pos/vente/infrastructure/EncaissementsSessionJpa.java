package ma.invento.pos.vente.infrastructure;

import java.math.BigDecimal;
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
}
