package ma.invento.pos.facture.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import ma.invento.pos.client.domain.TypeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Factures émises et compteur annuel de leurs numéros. */
@Component
public class FacturesJdbc {

    /** Facture émise, avec l'identité du client figée à l'émission. */
    public record Facture(
            String numero,
            long venteId,
            TypeClient clientType,
            String clientNom,
            String clientIce,
            String clientIdentifiantFiscal,
            String clientRc,
            String clientContact,
            String clientAdresse,
            Instant emiseLe) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final String s;

    FacturesJdbc(NamedParameterJdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        this.jdbc = jdbc;
        // Schéma issu de la configuration, jamais d'une saisie.
        this.s = schema + ".";
    }

    /**
     * Verrouille la vente jusqu'à la fin de la transaction : deux premières impressions simultanées
     * n'émettent qu'une facture.
     */
    public void verrouillerVente(long venteId) {
        jdbc.query("select id from " + s + "vente where id = :id for update", Map.of("id", venteId), r -> {});
    }

    public Optional<Facture> parVente(long venteId) {
        return jdbc
                .query(
                        "select * from " + s + "facture where vente_id = :id",
                        Map.of("id", venteId),
                        (r, i) -> new Facture(
                                r.getString("numero"),
                                r.getLong("vente_id"),
                                TypeClient.valueOf(r.getString("client_type")),
                                r.getString("client_nom"),
                                r.getString("client_ice"),
                                r.getString("client_identifiant_fiscal"),
                                r.getString("client_rc"),
                                r.getString("client_contact"),
                                r.getString("client_adresse"),
                                r.getTimestamp("emise_le").toInstant()))
                .stream()
                .findFirst();
    }

    /** Prochain numéro de l'année, sans trou : mise à jour atomique du compteur, créé au premier. */
    public int prochainNumero(int annee) {
        return jdbc.queryForObject(
                "insert into " + s + "compteur_facture as c (annee, dernier) values (:annee, 1)"
                        + " on conflict (annee) do update set dernier = c.dernier + 1 returning dernier",
                Map.of("annee", annee),
                Integer.class);
    }

    public void inserer(Facture f, long emisePar) {
        jdbc.update(
                "insert into " + s + "facture (numero, vente_id, client_type, client_nom, client_ice,"
                        + " client_identifiant_fiscal, client_rc, client_contact, client_adresse, emise_par, emise_le)"
                        + " values (:numero, :vente, :type, :nom, :ice, :if, :rc, :contact, :adresse, :par, :le)",
                new MapSqlParameterSource("numero", f.numero())
                        .addValue("vente", f.venteId())
                        .addValue("type", f.clientType().name())
                        .addValue("nom", f.clientNom())
                        .addValue("ice", f.clientIce())
                        .addValue("if", f.clientIdentifiantFiscal())
                        .addValue("rc", f.clientRc())
                        .addValue("contact", f.clientContact())
                        .addValue("adresse", f.clientAdresse())
                        .addValue("par", emisePar)
                        .addValue("le", Timestamp.from(f.emiseLe())));
    }
}
