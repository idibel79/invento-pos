package ma.invento.pos.catalogue.infrastructure;

import java.util.List;
import java.util.Optional;
import ma.invento.pos.catalogue.domain.Categorie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorieRepository extends JpaRepository<Categorie, Long> {

    Optional<Categorie> findByNomIgnoreCase(String nom);

    List<Categorie> findAllByOrderByNomAsc();
}
