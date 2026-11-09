package ma.invento.pos.vente.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import ma.invento.pos.vente.domain.TypeRemise;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lecture du journal des remises : une ligne par remise sur une ligne de vente et une par remise sur
 * le ticket, pour les ventes validées, de la plus récente à la plus ancienne. Lu en SQL (union des deux
 * sortes de remise, filtres et pagination côté base) pour ne charger ni les ventes ni leurs lignes en
 * entités.
 */
@Component
public class JournalRemisesRequete {

    /** Remise telle que le journal l'affiche. */
    public record RemiseJournal(
            String venteNumero,
            Instant le,
            String caisse,
            String vendeur,
            String autorisePar,
            Portee portee,
            String produit,
            Integer quantite,
            TypeRemise type,
            BigDecimal valeur,
            BigDecimal montant,
            String motif) {}

    /** Totaux de la sélection : nombre de remises, montant remisé, remises autorisées par un tiers. */
    public record ResumeRemises(long nombre, BigDecimal montantTotal, long autorisees) {}

    /** Remise posée sur une ligne (un produit) ou sur le total du ticket. */
    public enum Portee {
        LIGNE,
        TICKET
    }

    /**
     * Sélection : remises validées depuis une date, d'une seule portée, d'une caisse et d'un vendeur ; un
     * critère null ne filtre pas.
     */
    public record Filtre(Instant depuis, Portee portee, Long caisseId, Long vendeurId) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final String lignes;
    private final String tickets;

    JournalRemisesRequete(NamedParameterJdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        this.jdbc = jdbc;
        // Schéma issu de la configuration (jamais d'une saisie), comme pour la numérotation des ventes.
        String s = schema + ".";
        String communs = " from " + s + "vente v"
                + " join " + s + "caisse c on c.id = v.caisse_id"
                + " join " + s + "utilisateur uv on uv.id = v.caissier_id";
        // Chaque remise d'article a son autorisation ; la remise ticket, celle de la vente.
        String autoriseLigne =
                " left join " + s + "utilisateur ua on ua.id = coalesce(lv.remise_validee_par, v.validee_par)";
        String autoriseTicket = " left join " + s + "utilisateur ua on ua.id = v.validee_par";
        String colonnesVente = "v.numero, v.finalisee_le, c.nom as caisse,"
                + " uv.prenom || ' ' || uv.nom as vendeur, ua.prenom || ' ' || ua.nom as autorise_par,"
                + " v.motif_remise as motif";
        this.lignes = "select " + colonnesVente + ", 'LIGNE' as portee, lv.designation as produit,"
                + " lv.quantite, lv.remise_type as type, lv.remise_valeur as valeur, lv.remise_montant as montant"
                + communs
                + " join " + s + "ligne_vente lv on lv.vente_id = v.id"
                + autoriseLigne
                + " where v.statut = 'VALIDEE' and lv.remise_montant > 0";
        this.tickets = "select " + colonnesVente + ", 'TICKET' as portee, null as produit, null as quantite,"
                + " v.remise_ticket_type as type, v.remise_ticket_valeur as valeur,"
                + " v.remise_ticket_montant as montant"
                + communs
                + autoriseTicket
                + " where v.statut = 'VALIDEE' and v.remise_ticket_montant > 0";
    }

    public List<RemiseJournal> lister(Filtre filtre, int page, int taille) {
        MapSqlParameterSource parametres =
                parametres(filtre).addValue("taille", taille).addValue("decalage", (long) page * taille);
        return jdbc.query(
                "select * from (" + union(filtre) + ") r"
                        + " order by r.finalisee_le desc, r.numero desc limit :taille offset :decalage",
                parametres,
                JournalRemisesRequete::versRemise);
    }

    public ResumeRemises resumer(Filtre filtre) {
        return jdbc.queryForObject(
                "select count(*) as nombre, coalesce(sum(r.montant), 0) as total, count(r.autorise_par) as autorisees"
                        + " from (" + union(filtre) + ") r",
                parametres(filtre),
                (r, i) -> new ResumeRemises(r.getLong("nombre"), r.getBigDecimal("total"), r.getLong("autorisees")));
    }

    /** Union des remises retenues par le filtre ; les conditions sont fixes, seules les valeurs varient. */
    private String union(Filtre filtre) {
        String conditions = (filtre.depuis() == null ? "" : " and v.finalisee_le >= :depuis")
                + (filtre.caisseId() == null ? "" : " and v.caisse_id = :caisseId")
                + (filtre.vendeurId() == null ? "" : " and v.caissier_id = :vendeurId");
        List<String> parties = new ArrayList<>();
        if (filtre.portee() != Portee.TICKET) parties.add(lignes + conditions);
        if (filtre.portee() != Portee.LIGNE) parties.add(tickets + conditions);
        return String.join(" union all ", parties);
    }

    private static MapSqlParameterSource parametres(Filtre filtre) {
        MapSqlParameterSource parametres = new MapSqlParameterSource();
        if (filtre.depuis() != null) parametres.addValue("depuis", Timestamp.from(filtre.depuis()));
        if (filtre.caisseId() != null) parametres.addValue("caisseId", filtre.caisseId());
        if (filtre.vendeurId() != null) parametres.addValue("vendeurId", filtre.vendeurId());
        return parametres;
    }

    private static RemiseJournal versRemise(ResultSet r, int ligne) throws SQLException {
        String type = r.getString("type");
        return new RemiseJournal(
                r.getString("numero"),
                r.getTimestamp("finalisee_le").toInstant(),
                r.getString("caisse"),
                r.getString("vendeur"),
                r.getString("autorise_par"),
                Portee.valueOf(r.getString("portee")),
                r.getString("produit"),
                (Integer) r.getObject("quantite"),
                type == null ? null : TypeRemise.valueOf(type),
                r.getBigDecimal("valeur"),
                r.getBigDecimal("montant"),
                r.getString("motif"));
    }
}
