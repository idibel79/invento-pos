package ma.invento.pos.caisse.infrastructure;

import java.util.List;
import ma.invento.pos.caisse.domain.Caisse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaisseRepository extends JpaRepository<Caisse, Long> {

    List<Caisse> findAllByOrderByNomAsc();

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);

    long countByIdIn(Iterable<Long> ids);
}
