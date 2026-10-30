package ma.invento.pos.identite.infrastructure;

import java.util.List;
import java.util.Optional;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    Optional<Utilisateur> findByRole(Role role);

    boolean existsByRole(Role role);

    List<Utilisateur> findAllByOrderByNomAscPrenomAsc();

    /** Incrément atomique, sans conflit de version entre deux essais simultanés. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Utilisateur u set u.essaisRates = u.essaisRates + 1 where u.id = :id")
    void incrementerEssaisRates(@Param("id") long id);
}
