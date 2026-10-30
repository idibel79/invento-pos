package ma.invento.pos.identite.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** Code de secours de l'admin : montré une seule fois, stocké haché (BCrypt), à usage unique. */
@Entity
@Table(name = "code_secours")
public class CodeSecours {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "code_secours_seq")
    @SequenceGenerator(name = "code_secours_seq", sequenceName = "code_secours_seq", allocationSize = 50)
    private Long id;

    private String codeHash;
    private Instant creeLe;
    private Instant utiliseLe;

    protected CodeSecours() {}

    public CodeSecours(String codeHash, Instant creeLe) {
        this.codeHash = codeHash;
        this.creeLe = creeLe;
    }

    public void marquerUtilise(Instant maintenant) {
        this.utiliseLe = maintenant;
    }

    public String getCodeHash() {
        return codeHash;
    }
}
