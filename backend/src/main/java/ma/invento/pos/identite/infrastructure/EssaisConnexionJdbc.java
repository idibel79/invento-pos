package ma.invento.pos.identite.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Compteurs d'essais ratés, mis à jour par des {@code upsert} atomiques : plusieurs instances de
 * l'API peuvent compter en même temps sans perdre d'essai.
 */
@Component
public class EssaisConnexionJdbc {

    private final NamedParameterJdbcTemplate jdbc;
    private final String limites;
    private final String inconnus;

    EssaisConnexionJdbc(NamedParameterJdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        this.jdbc = jdbc;
        // Schéma issu de la configuration, jamais d'une saisie.
        this.limites = schema + ".limite_essai";
        this.inconnus = schema + ".essai_identifiant_inconnu";
    }

    public int echecs(String cle, Instant fenetre) {
        return jdbc
                .query(
                        "select echecs from " + limites + " where cle = :cle and fenetre = :fenetre",
                        new MapSqlParameterSource("cle", cle).addValue("fenetre", Timestamp.from(fenetre)),
                        (r, i) -> r.getInt(1))
                .stream()
                .findFirst()
                .orElse(0);
    }

    public void ajouterEchec(String cle, Instant fenetre) {
        jdbc.update(
                "insert into " + limites + " (cle, fenetre, echecs) values (:cle, :fenetre, 1)"
                        + " on conflict (cle, fenetre) do update set echecs = " + limites + ".echecs + 1",
                new MapSqlParameterSource("cle", cle).addValue("fenetre", Timestamp.from(fenetre)));
    }

    /** Ajoute un essai raté à un identifiant inconnu et renvoie le nombre d'essais cumulés. */
    public int ajouterEssaiInconnu(String identifiantHash, Instant maintenant) {
        return jdbc.queryForObject(
                "insert into " + inconnus + " (identifiant_hash, essais, mis_a_jour) values (:h, 1, :t)"
                        + " on conflict (identifiant_hash) do update set essais = " + inconnus
                        + ".essais + 1, mis_a_jour = :t returning essais",
                new MapSqlParameterSource("h", identifiantHash).addValue("t", Timestamp.from(maintenant)),
                Integer.class);
    }

    /** Supprime les fenêtres terminées et les identifiants inconnus oubliés ; renvoie le nombre de lignes. */
    public int purger(Instant fenetresAvant, Instant inconnusAvant) {
        return jdbc.update("delete from " + limites + " where fenetre < :f", Map.of("f", Timestamp.from(fenetresAvant)))
                + jdbc.update(
                        "delete from " + inconnus + " where mis_a_jour < :i",
                        Map.of("i", Timestamp.from(inconnusAvant)));
    }
}
