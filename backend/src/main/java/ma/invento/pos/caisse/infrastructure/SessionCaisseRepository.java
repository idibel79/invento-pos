package ma.invento.pos.caisse.infrastructure;

import jakarta.persistence.LockModeType;
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
}
