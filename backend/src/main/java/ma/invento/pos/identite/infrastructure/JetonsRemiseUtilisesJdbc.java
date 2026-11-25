package ma.invento.pos.identite.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Jetons d'autorisation de remise déjà utilisés. L'inscription est atomique (clé primaire) : deux
 * ventes simultanées, même sur deux instances, ne peuvent pas utiliser le même jeton.
 */
@Component
public class JetonsRemiseUtilisesJdbc {

    private final NamedParameterJdbcTemplate jdbc;
    private final String table;

    JetonsRemiseUtilisesJdbc(NamedParameterJdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        this.jdbc = jdbc;
        // Schéma issu de la configuration, jamais d'une saisie.
        this.table = schema + ".jeton_remise_utilise";
    }

    public boolean estUtilise(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from " + table + " where id = :id)", Map.of("id", id), Boolean.class));
    }

    /** Inscrit le jeton ; renvoie false s'il l'était déjà. */
    public boolean utiliser(UUID id, Instant expireLe) {
        return jdbc.update(
                        "insert into " + table + " (id, expire_le) values (:id, :e) on conflict (id) do nothing",
                        new MapSqlParameterSource("id", id).addValue("e", Timestamp.from(expireLe)))
                == 1;
    }

    /** Supprime les jetons échus, qui ne peuvent plus être présentés ; renvoie le nombre de lignes. */
    public int purger(Instant avant) {
        return jdbc.update("delete from " + table + " where expire_le < :a", Map.of("a", Timestamp.from(avant)));
    }
}
