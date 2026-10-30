package ma.invento.pos.vente.infrastructure;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ma.invento.pos.vente.domain.StatutVente;
import ma.invento.pos.vente.domain.Vente;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VenteRepository extends JpaRepository<Vente, Long> {

    @EntityGraph(attributePaths = {"lignes"})
    Optional<Vente> findByCleIdempotence(String cleIdempotence);

    /** Verrou de ligne : reprise, validation, annulation et échéance ne se croisent pas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vente v where v.id = :id")
    Optional<Vente> findForUpdate(@Param("id") long id);

    @EntityGraph(attributePaths = {"lignes"})
    List<Vente> findBySessionCaisseIdAndStatutOrderByCreeLeAsc(Long sessionCaisseId, StatutVente statut);

    @Query("select v.id from Vente v where v.statut = ma.invento.pos.vente.domain.StatutVente.EN_ATTENTE"
            + " and v.attenteExpireLe <= :maintenant")
    List<Long> findIdsAttentesExpirees(@Param("maintenant") Instant maintenant);

    @Query("select v.id from Vente v where v.statut = ma.invento.pos.vente.domain.StatutVente.EN_ATTENTE"
            + " and v.sessionCaisseId = :sessionId")
    List<Long> findIdsEnAttente(@Param("sessionId") long sessionId);

    @Query("select coalesce(sum(p.montant), 0) from Paiement p"
            + " where p.vente.sessionCaisseId = :sessionId"
            + " and p.vente.statut = ma.invento.pos.vente.domain.StatutVente.VALIDEE"
            + " and p.mode = ma.invento.pos.vente.domain.ModePaiement.ESPECES")
    BigDecimal sommeEspeces(@Param("sessionId") long sessionId);
}
