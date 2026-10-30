package ma.invento.pos.catalogue.infrastructure;

import java.util.List;
import ma.invento.pos.catalogue.domain.Tva;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TvaRepository extends JpaRepository<Tva, Long> {

    List<Tva> findAllByOrderByTauxAsc();
}
