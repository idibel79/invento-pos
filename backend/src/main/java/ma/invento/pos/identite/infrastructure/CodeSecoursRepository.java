package ma.invento.pos.identite.infrastructure;

import java.util.List;
import ma.invento.pos.identite.domain.CodeSecours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CodeSecoursRepository extends JpaRepository<CodeSecours, Long> {

    List<CodeSecours> findByUtiliseLeIsNull();

    /** Un seul code valide à la fois : les anciens codes non utilisés sont supprimés. */
    @Modifying(flushAutomatically = true)
    @Query("delete from CodeSecours c where c.utiliseLe is null")
    void supprimerCodesNonUtilises();
}
