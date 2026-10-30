package ma.invento.pos.caisse.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import ma.invento.pos.shared.domain.RegleMetierException;

/**
 * Session d'un caissier sur une caisse, de l'ouverture (fond de caisse) à la clôture (comptage
 * des espèces et écart). Une seule session ouverte par caisse et par caissier.
 */
@Entity
@Table(name = "session_caisse")
public class SessionCaisse {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "session_caisse_seq")
    @SequenceGenerator(name = "session_caisse_seq", sequenceName = "session_caisse_seq", allocationSize = 50)
    private Long id;

    private Long caisseId;
    private Long caissierId;
    private Instant ouverture;
    private Instant fermeture;
    private BigDecimal fondInitial;
    private BigDecimal montantCompte;
    private BigDecimal ecart;

    protected SessionCaisse() {}

    public SessionCaisse(long caisseId, long caissierId, BigDecimal fondInitial, Instant ouverture) {
        if (fondInitial.signum() < 0) {
            throw new RegleMetierException("Le fond de caisse ne peut pas être négatif.");
        }
        this.caisseId = caisseId;
        this.caissierId = caissierId;
        this.fondInitial = fondInitial;
        this.ouverture = ouverture;
    }

    /** Écart = espèces comptées − (fond de caisse + espèces encaissées pendant la session). */
    public void cloturer(BigDecimal montantCompte, BigDecimal especesEncaissees, Instant maintenant) {
        if (!estOuverte()) {
            throw new RegleMetierException("Cette session de caisse est déjà clôturée.");
        }
        if (montantCompte.signum() < 0) {
            throw new RegleMetierException("Le montant compté ne peut pas être négatif.");
        }
        this.montantCompte = montantCompte;
        this.ecart = montantCompte.subtract(fondInitial.add(especesEncaissees));
        this.fermeture = maintenant;
    }

    public boolean estOuverte() {
        return fermeture == null;
    }

    public Long getId() {
        return id;
    }

    public Long getCaisseId() {
        return caisseId;
    }

    public Long getCaissierId() {
        return caissierId;
    }

    public Instant getOuverture() {
        return ouverture;
    }

    public Instant getFermeture() {
        return fermeture;
    }

    public BigDecimal getFondInitial() {
        return fondInitial;
    }

    public BigDecimal getMontantCompte() {
        return montantCompte;
    }

    public BigDecimal getEcart() {
        return ecart;
    }
}
