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
import ma.invento.pos.vente.domain.PanierCalcule.LigneCalculee;

/** Ligne de ticket : prix et taux de TVA figés au moment de la vente. */
@Entity
@Table(name = "ligne_vente")
public class LigneVente {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ligne_vente_seq")
    @SequenceGenerator(name = "ligne_vente_seq", sequenceName = "ligne_vente_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vente_id")
    private Vente vente;

    private Long produitId;
    private int quantite;
    private BigDecimal prixUnitaire;
    private BigDecimal tauxTva;

    @Enumerated(EnumType.STRING)
    private TypeRemise remiseType;

    private BigDecimal remiseValeur;
    private BigDecimal remiseMontant;

    protected LigneVente() {}

    LigneVente(Vente vente, LigneCalculee calculee) {
        LignePanier ligne = calculee.ligne();
        this.vente = vente;
        this.produitId = ligne.produitId();
        this.quantite = ligne.quantite();
        this.prixUnitaire = ligne.prixUnitaire();
        this.tauxTva = ligne.tauxTva();
        this.remiseType = ligne.remise() == null ? null : ligne.remise().type();
        this.remiseValeur = ligne.remise() == null ? null : ligne.remise().valeur();
        this.remiseMontant = calculee.remiseMontant();
    }

    public Long getProduitId() {
        return produitId;
    }

    public int getQuantite() {
        return quantite;
    }

    public BigDecimal getPrixUnitaire() {
        return prixUnitaire;
    }

    public BigDecimal getTauxTva() {
        return tauxTva;
    }

    public Remise getRemise() {
        return Remise.ou(remiseType, remiseValeur);
    }

    public BigDecimal getRemiseMontant() {
        return remiseMontant;
    }
}
