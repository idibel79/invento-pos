package ma.invento.pos.caisse.infrastructure;

import java.util.List;
import java.util.Optional;
import ma.invento.pos.caisse.domain.Caisse;
import org.springframework.data.jpa.repository.JpaRepository;

/** Les caisses supprimées (logiquement) ne sont visibles que par leur id, pour l'historique. */
public interface CaisseRepository extends JpaRepository<Caisse, Long> {

    List<Caisse> findAllBySupprimeeFalseOrderByNomAsc();

    Optional<Caisse> findByIdAndSupprimeeFalse(Long id);

    boolean existsByNomIgnoreCaseAndSupprimeeFalse(String nom);

    boolean existsByNomIgnoreCaseAndIdNotAndSupprimeeFalse(String nom, Long id);

    long countByIdInAndSupprimeeFalse(Iterable<Long> ids);
}
