package ma.invento.pos.caisse.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** Poste de caisse (mode en ligne : plusieurs caisses partagent le catalogue et le stock). */
@Entity
@Table(name = "caisse")
public class Caisse {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "caisse_seq")
    @SequenceGenerator(name = "caisse_seq", sequenceName = "caisse_seq", allocationSize = 50)
    private Long id;

    private String nom;
    private boolean active = true;

    protected Caisse() {}

    public Caisse(String nom) {
        this.nom = nom;
    }

    public void modifier(String nom, boolean active) {
        this.nom = nom;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public boolean isActive() {
        return active;
    }
}
