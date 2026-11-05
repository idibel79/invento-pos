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
 * Session d'un caissier sur une caisse, de l'ouverture (fond de caisse pris sur son compte) à la
 * clôture (comptage du tiroir, espèces et tickets TPE, et écart). Une seule session ouverte par
 * caisse et par caissier.
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
    private boolean clotureForcee;
    private boolean comptageEnAttente;

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

    /** Écart = compté dans le tiroir − attendu (fond + ventes + reçu − versé pendant la session). */
    public void cloturer(BigDecimal montantCompte, ComptageTiroir tiroir, Instant maintenant) {
        if (!estOuverte()) {
            throw new RegleMetierException("Cette session de caisse est déjà clôturée.");
        }
        enregistrerComptage(montantCompte, tiroir);
        this.fermeture = maintenant;
    }

    /**
     * Clôture d'office (caisse désactivée) : le tiroir n'a pas été compté, la session attend que l'admin
     * le compte ({@link #terminerComptage}).
     */
    public void cloturerDOffice(Instant maintenant) {
        if (!estOuverte()) {
            throw new RegleMetierException("Cette session de caisse est déjà clôturée.");
        }
        this.clotureForcee = true;
        this.comptageEnAttente = true;
        this.fermeture = maintenant;
    }

    /** Comptage du tiroir par l'admin après une clôture d'office. */
    public void terminerComptage(BigDecimal montantCompte, ComptageTiroir tiroir) {
        if (!comptageEnAttente) {
            throw new RegleMetierException("Le tiroir de cette session a déjà été compté.");
        }
        enregistrerComptage(montantCompte, tiroir);
        this.comptageEnAttente = false;
    }

    private void enregistrerComptage(BigDecimal montantCompte, ComptageTiroir tiroir) {
        if (montantCompte.signum() < 0) {
            throw new RegleMetierException("Le montant compté ne peut pas être négatif.");
        }
        this.montantCompte = montantCompte;
        this.ecart = montantCompte.subtract(tiroir.attendu(fondInitial));
    }

    public boolean estOuverte() {
        return fermeture == null;
    }

    public boolean isClotureForcee() {
        return clotureForcee;
    }

    public boolean isComptageEnAttente() {
        return comptageEnAttente;
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
