package ma.invento.pos.tresorerie.infrastructure;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import ma.invento.pos.tresorerie.domain.TypeCompte;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Comptes de trésorerie. Les soldes ne changent que par des mises à jour conditionnelles atomiques :
 * deux opérations simultanées ne peuvent pas rendre un solde inférieur à son plancher.
 */
@Component
public class ComptesJdbc {

    public record Compte(long id, TypeCompte type, Long utilisateurId, BigDecimal solde) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final String table;

    ComptesJdbc(NamedParameterJdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        this.jdbc = jdbc;
        // Schéma issu de la configuration, jamais d'une saisie.
        this.table = schema + ".compte_tresorerie";
    }

    public Compte principale() {
        return jdbc.queryForObject(
                "select id, type, utilisateur_id, solde from " + table + " where type = 'PRINCIPALE'",
                Map.of(),
                (r, i) -> versCompte(r));
    }

    public Optional<Compte> caissier(long utilisateurId) {
        return jdbc
                .query(
                        "select id, type, utilisateur_id, solde from " + table + " where utilisateur_id = :u",
                        Map.of("u", utilisateurId),
                        (r, i) -> versCompte(r))
                .stream()
                .findFirst();
    }

    /** Compte du caissier, créé à 0 s'il n'existe pas encore (utilisateur créé après la mise en place). */
    public Compte caissierOuCree(long utilisateurId) {
        jdbc.update(
                "insert into " + table + " (type, utilisateur_id, solde) values ('CAISSIER', :u, 0)"
                        + " on conflict (utilisateur_id) do nothing",
                Map.of("u", utilisateurId));
        return caissier(utilisateurId).orElseThrow();
    }

    public Compte parId(long id) {
        return jdbc.queryForObject(
                "select id, type, utilisateur_id, solde from " + table + " where id = :id",
                Map.of("id", id),
                (r, i) -> versCompte(r));
    }

    /**
     * Débite le compte si son solde reste supérieur ou égal au plancher ; renvoie le nouveau solde, vide
     * si le solde ne le permet pas.
     */
    public Optional<BigDecimal> debiter(long compteId, BigDecimal montant, BigDecimal plancher) {
        return jdbc
                .query(
                        "update " + table + " set solde = solde - :m where id = :id and solde - :m >= :plancher"
                                + " returning solde",
                        new MapSqlParameterSource("id", compteId)
                                .addValue("m", montant)
                                .addValue("plancher", plancher),
                        (r, i) -> r.getBigDecimal("solde"))
                .stream()
                .findFirst();
    }

    public BigDecimal crediter(long compteId, BigDecimal montant) {
        return jdbc.queryForObject(
                "update " + table + " set solde = solde + :m where id = :id returning solde",
                new MapSqlParameterSource("id", compteId).addValue("m", montant),
                BigDecimal.class);
    }

    /** Solde stocké de chaque caissier, par utilisateur. */
    public Map<Long, BigDecimal> soldesCaissiers() {
        Map<Long, BigDecimal> soldes = new HashMap<>();
        jdbc.query("select utilisateur_id, solde from " + table + " where type = 'CAISSIER'", r -> {
            soldes.put(r.getLong("utilisateur_id"), r.getBigDecimal("solde"));
        });
        return soldes;
    }

    private static Compte versCompte(java.sql.ResultSet r) throws java.sql.SQLException {
        return new Compte(
                r.getLong("id"),
                TypeCompte.valueOf(r.getString("type")),
                r.getObject("utilisateur_id", Long.class),
                r.getBigDecimal("solde"));
    }
}
