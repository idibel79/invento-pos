package ma.invento.pos.vente.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "paiement")
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "paiement_seq")
    @SequenceGenerator(name = "paiement_seq", sequenceName = "paiement_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vente_id")
    private Vente vente;

    @Enumerated(EnumType.STRING)
    private ModePaiement mode;

    private BigDecimal montant;
    private BigDecimal montantRecu;
    private BigDecimal rendu;

    protected Paiement() {}

    Paiement(Vente vente, ModePaiement mode, BigDecimal montant, BigDecimal montantRecu) {
        this.vente = vente;
        this.mode = mode;
        this.montant = montant;
        this.montantRecu = montantRecu;
        this.rendu = montantRecu.subtract(montant);
    }

    public ModePaiement getMode() {
        return mode;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public BigDecimal getMontantRecu() {
        return montantRecu;
    }

    public BigDecimal getRendu() {
        return rendu;
    }
}
