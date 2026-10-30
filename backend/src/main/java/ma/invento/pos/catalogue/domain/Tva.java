package ma.invento.pos.catalogue.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "tva")
public class Tva {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tva_seq")
    @SequenceGenerator(name = "tva_seq", sequenceName = "tva_seq", allocationSize = 50)
    private Long id;

    private String libelle;

    /** Taux en pourcentage, ex. 20.00. */
    private BigDecimal taux;

    protected Tva() {}

    public Tva(String libelle, BigDecimal taux) {
        this.libelle = libelle;
        this.taux = taux;
    }

    public Long getId() {
        return id;
    }

    public String getLibelle() {
        return libelle;
    }

    public BigDecimal getTaux() {
        return taux;
    }
}
