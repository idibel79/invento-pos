package ma.invento.pos.caisse.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import ma.invento.pos.caisse.domain.SessionCaisse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionCaisseRepository extends JpaRepository<SessionCaisse, Long> {

    Optional<SessionCaisse> findByCaissierIdAndFermetureIsNull(Long caissierId);

    boolean existsByCaisseIdAndFermetureIsNull(Long caisseId);

    /** Verrou de ligne : une clôture et une vente ne se croisent pas sur la même session. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessionCaisse s where s.id = :id")
    Optional<SessionCaisse> findForUpdate(@Param("id") long id);

    /**
     * Session ouverte du caissier, verrouillée. Le filtre sur la fermeture est réévalué par
     * PostgreSQL après l'attente du verrou : une session clôturée entre-temps n'est pas renvoyée.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessionCaisse s where s.caissierId = :caissierId and s.fermeture is null")
    Optional<SessionCaisse> findOuverteDuCaissierForUpdate(@Param("caissierId") long caissierId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessionCaisse s where s.caisseId = :caisseId and s.fermeture is null")
    Optional<SessionCaisse> findOuverteDeLaCaisseForUpdate(@Param("caisseId") long caisseId);

    Optional<SessionCaisse> findFirstByCaissierIdOrderByOuvertureDesc(Long caissierId);

    List<SessionCaisse> findByFermetureIsNull();

    /** Sessions clôturées d'office dont l'admin n'a pas encore compté le tiroir. */
    List<SessionCaisse> findByComptageEnAttenteTrueOrderByFermetureAsc();

    boolean existsByCaisseIdAndComptageEnAttenteTrue(Long caisseId);
}
