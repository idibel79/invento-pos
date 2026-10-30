package ma.invento.pos.stock.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** Journal des variations de stock : chaque changement de stock en laisse une trace. */
@Entity
@Table(name = "mouvement_stock")
public class MouvementStock {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mouvement_stock_seq")
    @SequenceGenerator(name = "mouvement_stock_seq", sequenceName = "mouvement_stock_seq", allocationSize = 50)
    private Long id;

    private Long produitId;

    @Enumerated(EnumType.STRING)
    private TypeMouvement type;

    /** Variation signée (+ entrée, - sortie). */
    private int quantite;

    private String reference;
    private Instant creeLe;
    private Long utilisateurId;

    protected MouvementStock() {}

    public MouvementStock(
            Long produitId, TypeMouvement type, int quantite, String reference, Instant creeLe, Long utilisateurId) {
        this.produitId = produitId;
        this.type = type;
        this.quantite = quantite;
        this.reference = reference;
        this.creeLe = creeLe;
        this.utilisateurId = utilisateurId;
    }

    public Long getProduitId() {
        return produitId;
    }

    public TypeMouvement getType() {
        return type;
    }

    public int getQuantite() {
        return quantite;
    }

    public String getReference() {
        return reference;
    }
}
