package ma.invento.pos.catalogue.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "produit")
public class Produit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "produit_seq")
    @SequenceGenerator(name = "produit_seq", sequenceName = "produit_seq", allocationSize = 50)
    private Long id;

    private String pid;
    private String designation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categorie_id")
    private Categorie categorie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tva_id")
    private Tva tva;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    private BigDecimal ppv;
    private BigDecimal pph;
    private BigDecimal tauxMarge;
    private int stock;
    private int stockReserve;
    private Integer stockMin;
    private Integer stockMax;
    private LocalDate datePeremption;
    private boolean actif = true;

    @Version
    private long version;

    private Instant creeLe;
    private Instant modifieLe;

    @OneToMany(mappedBy = "produit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 100)
    private List<CodeBarre> codesBarres = new ArrayList<>();

    protected Produit() {}

    public Produit(String pid, Instant creeLe) {
        this.pid = pid;
        this.creeLe = creeLe;
        this.modifieLe = creeLe;
    }

    /** Met à jour la fiche (hors stock, qui ne change que par des mouvements). */
    public void modifierFiche(FicheProduit fiche, Instant maintenant) {
        this.designation = fiche.designation();
        this.categorie = fiche.categorie();
        this.tva = fiche.tva();
        this.zone = fiche.zone();
        this.ppv = fiche.ppv();
        this.pph = fiche.pph();
        this.tauxMarge = fiche.tauxMarge();
        this.stockMin = fiche.stockMin();
        this.stockMax = fiche.stockMax();
        this.datePeremption = fiche.datePeremption();
        this.actif = fiche.actif();
        this.modifieLe = maintenant;
    }

    /**
     * Fixe le stock à la valeur importée et renvoie la variation, à inscrire comme mouvement
     * {@code IMPORT}.
     */
    public int fixerStockImporte(int nouveauStock, Instant maintenant) {
        int variation = nouveauStock - stock;
        this.stock = nouveauStock;
        this.modifieLe = maintenant;
        return variation;
    }

    /**
     * Remplace les codes-barres en conservant les lignes déjà présentes (évite de supprimer puis
     * recréer un même code, ce qui violerait la contrainte d'unicité au flush).
     */
    public void remplacerCodesBarres(Collection<String> codes) {
        Set<String> voulus = new LinkedHashSet<>(codes);
        codesBarres.removeIf(existant -> !voulus.contains(existant.getCode()));
        codesBarres.forEach(existant -> voulus.remove(existant.getCode()));
        voulus.forEach(code -> codesBarres.add(new CodeBarre(this, code)));
    }

    /** Stock vendable : stock physique moins les quantités réservées par les ventes en attente. */
    public int getStockDisponible() {
        return stock - stockReserve;
    }

    /** Rupture : vente bloquée (stock disponible nul ou négatif). */
    public boolean isEnRupture() {
        return getStockDisponible() <= 0;
    }

    public Long getId() {
        return id;
    }

    public String getPid() {
        return pid;
    }

    public String getDesignation() {
        return designation;
    }

    public Categorie getCategorie() {
        return categorie;
    }

    public Tva getTva() {
        return tva;
    }

    public Zone getZone() {
        return zone;
    }

    public BigDecimal getPpv() {
        return ppv;
    }

    public BigDecimal getPph() {
        return pph;
    }

    public BigDecimal getTauxMarge() {
        return tauxMarge;
    }

    public int getStock() {
        return stock;
    }

    public int getStockReserve() {
        return stockReserve;
    }

    public Integer getStockMin() {
        return stockMin;
    }

    public Integer getStockMax() {
        return stockMax;
    }

    public LocalDate getDatePeremption() {
        return datePeremption;
    }

    public boolean isActif() {
        return actif;
    }

    public long getVersion() {
        return version;
    }

    public List<String> getCodesBarres() {
        return codesBarres.stream().map(CodeBarre::getCode).toList();
    }
}
