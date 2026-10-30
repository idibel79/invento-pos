package ma.invento.pos.vente.infrastructure;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Numéro de ticket : préfixe de la caisse et compteur PostgreSQL (C1-000128), sans trou de verrou. */
@Component
public class NumeroteurVente {

    private final EntityManager entityManager;
    private final String requete;

    NumeroteurVente(EntityManager entityManager, @Value("${spring.flyway.default-schema}") String schema) {
        this.entityManager = entityManager;
        // Schéma issu de la configuration (jamais d'une saisie) : la séquence n'est pas dans le search_path.
        this.requete = "select nextval('" + schema + ".vente_numero_seq')";
    }

    public String suivant(long caisseId) {
        Number valeur = (Number) entityManager.createNativeQuery(requete).getSingleResult();
        return "C%d-%06d".formatted(caisseId, valeur.longValue());
    }
}
