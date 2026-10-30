package ma.invento.pos.catalogue.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "code_barre")
public class CodeBarre {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "code_barre_seq")
    @SequenceGenerator(name = "code_barre_seq", sequenceName = "code_barre_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produit_id")
    private Produit produit;

    private String code;

    protected CodeBarre() {}

    CodeBarre(Produit produit, String code) {
        this.produit = produit;
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
