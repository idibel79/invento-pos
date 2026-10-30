package ma.invento.pos.catalogue.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import ma.invento.pos.catalogue.domain.Produit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProduitRepository extends JpaRepository<Produit, Long>, JpaSpecificationExecutor<Produit> {

    /** Charge catégorie, zone et TVA dans la même requête ; les codes-barres suivent par lots. */
    @Override
    @EntityGraph(attributePaths = {"categorie", "zone", "tva"})
    Page<Produit> findAll(Specification<Produit> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"categorie", "zone", "tva"})
    Optional<Produit> findWithReferencesById(Long id);

    @EntityGraph(attributePaths = {"categorie", "zone", "tva"})
    @Query("select p from Produit p join p.codesBarres c where c.code = :code")
    Optional<Produit> findByCodeBarre(@Param("code") String code);

    @Query("select p.id from Produit p join p.codesBarres c where c.code in :codes and p.id <> :produitId")
    List<Long> findAutresProduitsAvecCodes(
            @Param("codes") Collection<String> codes, @Param("produitId") long produitId);

    boolean existsByPid(String pid);

    @EntityGraph(attributePaths = {"tva"})
    List<Produit> findWithTvaByIdIn(Collection<Long> ids);

    // Mises à jour atomiques du stock : la condition est vérifiée par PostgreSQL sur la ligne
    // verrouillée, jamais seulement côté Java. La version augmente pour qu'une modification
    // concurrente de la fiche (verrouillage optimiste) ne réécrive pas un stock périmé.

    /** Réserve {@code quantite} si le stock disponible suffit ; renvoie 0 sinon. */
    @Modifying(flushAutomatically = true)
    @Query("update Produit p set p.stockReserve = p.stockReserve + :quantite, p.version = p.version + 1"
            + " where p.id = :id and p.actif = true and p.stock - p.stockReserve >= :quantite")
    int reserver(@Param("id") long id, @Param("quantite") int quantite);

    /** Libère une réservation ; renvoie 0 si la réserve est inférieure (incohérence). */
    @Modifying(flushAutomatically = true)
    @Query("update Produit p set p.stockReserve = p.stockReserve - :quantite, p.version = p.version + 1"
            + " where p.id = :id and p.stockReserve >= :quantite")
    int liberer(@Param("id") long id, @Param("quantite") int quantite);

    /** Sort {@code quantite} du stock si le stock disponible suffit ; renvoie 0 sinon. */
    @Modifying(flushAutomatically = true)
    @Query("update Produit p set p.stock = p.stock - :quantite, p.version = p.version + 1"
            + " where p.id = :id and p.actif = true and p.stock - p.stockReserve >= :quantite")
    int sortir(@Param("id") long id, @Param("quantite") int quantite);

    /** Tous les produits avec leurs codes-barres, pour l'import (une requête par lot de 100). */
    @EntityGraph(attributePaths = {"categorie", "zone", "tva"})
    List<Produit> findAllBy();
}
