package ma.invento.pos.parametre.infrastructure;

import ma.invento.pos.parametre.domain.Parametre;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametreRepository extends JpaRepository<Parametre, String> {}
