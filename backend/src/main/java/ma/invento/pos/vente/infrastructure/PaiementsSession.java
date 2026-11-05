package ma.invento.pos.vente.infrastructure;

import java.math.BigDecimal;
import ma.invento.pos.vente.domain.ModePaiement;

/** Total encaissé sur une session pour un mode de paiement (projection de {@link VenteRepository#sommesParMode}). */
public record PaiementsSession(Long sessionId, ModePaiement mode, BigDecimal montant) {}
