package ma.invento.pos.vente.infrastructure;

import java.math.BigDecimal;

/** Espèces encaissées sur une session (projection de {@link VenteRepository#sommesEspeces}). */
public record EspecesSession(Long sessionId, BigDecimal montant) {}
