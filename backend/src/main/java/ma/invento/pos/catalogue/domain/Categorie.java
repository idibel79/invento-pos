package ma.invento.pos.catalogue.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "categorie")
public class Categorie {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "categorie_seq")
    @SequenceGenerator(name = "categorie_seq", sequenceName = "categorie_seq", allocationSize = 50)
    private Long id;

    private String nom;

    protected Categorie() {}

    public Categorie(String nom) {
        this.nom = nom;
    }

    public Long getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }
}
