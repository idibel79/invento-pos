package ma.invento.pos.stock.infrastructure;

import java.util.List;
import ma.invento.pos.stock.domain.MouvementStock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MouvementStockRepository extends JpaRepository<MouvementStock, Long> {

    List<MouvementStock> findByProduitIdOrderByIdAsc(Long produitId);
}
