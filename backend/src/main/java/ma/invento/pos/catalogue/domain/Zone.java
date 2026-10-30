package ma.invento.pos.catalogue.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "zone")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "zone_seq")
    @SequenceGenerator(name = "zone_seq", sequenceName = "zone_seq", allocationSize = 50)
    private Long id;

    private String nom;

    protected Zone() {}

    public Zone(String nom) {
        this.nom = nom;
    }

    public Long getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }
}
