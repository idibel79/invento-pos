package ma.invento.pos.parametre.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parametre")
public class Parametre {

    @Id
    private String cle;

    private String valeur;

    protected Parametre() {}

    public Parametre(String cle, String valeur) {
        this.cle = cle;
        this.valeur = valeur;
    }

    public String getCle() {
        return cle;
    }

    public String getValeur() {
        return valeur;
    }

    public void setValeur(String valeur) {
        this.valeur = valeur;
    }
}
