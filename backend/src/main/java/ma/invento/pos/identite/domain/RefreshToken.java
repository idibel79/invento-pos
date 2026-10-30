package ma.invento.pos.identite.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** Jeton de rafraîchissement, stocké haché (SHA-256) ; renouvelé à chaque usage. */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "refresh_token_seq")
    @SequenceGenerator(name = "refresh_token_seq", sequenceName = "refresh_token_seq", allocationSize = 50)
    private Long id;

    private Long utilisateurId;
    private String tokenHash;
    private Instant expireLe;
    private boolean revoque;
    private Instant creeLe;

    protected RefreshToken() {}

    public RefreshToken(Long utilisateurId, String tokenHash, Instant creeLe, Instant expireLe) {
        this.utilisateurId = utilisateurId;
        this.tokenHash = tokenHash;
        this.creeLe = creeLe;
        this.expireLe = expireLe;
    }

    public boolean estExpire(Instant maintenant) {
        return !maintenant.isBefore(expireLe);
    }

    public void revoquer() {
        this.revoque = true;
    }

    public Long getUtilisateurId() {
        return utilisateurId;
    }

    public boolean isRevoque() {
        return revoque;
    }
}
