package ma.invento.pos.identite.infrastructure;

import java.util.Optional;
import ma.invento.pos.identite.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revoque = true where t.utilisateurId = :utilisateurId and t.revoque = false")
    int revoquerTous(@Param("utilisateurId") long utilisateurId);
}
