package ma.invento.pos.catalogue.infrastructure;

import java.util.List;
import java.util.Optional;
import ma.invento.pos.catalogue.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    Optional<Zone> findByNomIgnoreCase(String nom);

    List<Zone> findAllByOrderByNomAsc();
}
