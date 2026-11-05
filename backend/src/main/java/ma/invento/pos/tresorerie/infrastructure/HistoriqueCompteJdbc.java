package ma.invento.pos.tresorerie.infrastructure;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import ma.invento.pos.tresorerie.domain.StatutVersement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Historique du compte d'un utilisateur, jour par jour : sessions de caisse, ventes agrégées par
 * session, versements et ajustements. Lu en SQL, une requête par nature d'événement.
 */
@Component
public class HistoriqueCompteJdbc {

    /** Session du caissier qui touche la journée (ouverte, clôturée ou en cours ce jour-là). */
    public record SessionJour(
            long id,
            String caisse,
            BigDecimal fond,
            Instant ouverture,
            Instant fermeture,
            BigDecimal montantCompte,
            BigDecimal ecart,
            boolean clotureForcee,
            boolean comptageEnAttente) {}

    /** Ventes validées d'une session sur la journée. */
    public record VentesJour(
            long sessionId, String caisse, int tickets, BigDecimal especes, BigDecimal carte, Instant derniere) {}

    /** Versement entre le compte et la caisse principale, créé dans la journée. */
    public record VersementJour(
            long id,
            boolean versLaPrincipale,
            BigDecimal montant,
            StatutVersement statut,
            String motif,
            boolean cloture,
            boolean autoValide,
            String creePar,
            Instant creeLe,
            String traitePar,
            Instant traiteLe,
            String caisse) {}

    /** Ajustement (passage en perte) fait par l'admin. */
    public record AjustementJour(BigDecimal montant, String motif, String auteur, Instant le) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final String s;

    HistoriqueCompteJdbc(NamedParameterJdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        this.jdbc = jdbc;
        // Schéma issu de la configuration, jamais d'une saisie.
        this.s = schema + ".";
    }

    /** Jours (dans le fuseau donné) de [debut, fin[ où l'utilisateur a au moins une opération. */
    public List<LocalDate> joursActifs(long utilisateurId, Instant debut, Instant fin, String fuseau) {
        MapSqlParameterSource p = periode(utilisateurId, debut, fin).addValue("fuseau", fuseau);
        return jdbc.query(
                "select distinct (t.le at time zone :fuseau)::date as jour from ("
                        + " select ouverture as le from " + s + "session_caisse where caissier_id = :u"
                        + " and ouverture >= :debut and ouverture < :fin"
                        + " union all select fermeture from " + s + "session_caisse where caissier_id = :u"
                        + " and fermeture >= :debut and fermeture < :fin"
                        + " union all select finalisee_le from " + s + "vente where caissier_id = :u"
                        + " and statut = 'VALIDEE' and finalisee_le >= :debut and finalisee_le < :fin"
                        + " union all select cree_le from " + s + "operation_tresorerie where caissier_id = :u"
                        + " and type <> 'CLOTURE_SESSION' and cree_le >= :debut and cree_le < :fin"
                        + ") t order by jour",
                p,
                (r, i) -> r.getObject("jour", LocalDate.class));
    }

    public List<SessionJour> sessions(long utilisateurId, Instant debut, Instant fin) {
        return jdbc.query(
                "select sc.id, c.nom, sc.fond_initial, sc.ouverture, sc.fermeture, sc.montant_compte, sc.ecart,"
                        + " sc.cloture_forcee, sc.comptage_en_attente"
                        + " from " + s + "session_caisse sc join " + s + "caisse c on c.id = sc.caisse_id"
                        + " where sc.caissier_id = :u and sc.ouverture < :fin"
                        + " and (sc.fermeture is null or sc.fermeture >= :debut) order by sc.ouverture",
                periode(utilisateurId, debut, fin),
                (r, i) -> new SessionJour(
                        r.getLong("id"),
                        r.getString("nom"),
                        r.getBigDecimal("fond_initial"),
                        VersementsJdbc.instant(r, "ouverture"),
                        VersementsJdbc.instant(r, "fermeture"),
                        r.getBigDecimal("montant_compte"),
                        r.getBigDecimal("ecart"),
                        r.getBoolean("cloture_forcee"),
                        r.getBoolean("comptage_en_attente")));
    }

    public List<VentesJour> ventes(long utilisateurId, Instant debut, Instant fin) {
        return jdbc.query(
                "select v.session_caisse_id, c.nom, count(distinct v.id) as tickets,"
                        + " coalesce(sum(p.montant) filter (where p.mode = 'ESPECES'), 0) as especes,"
                        + " coalesce(sum(p.montant) filter (where p.mode = 'CARTE'), 0) as carte,"
                        + " max(v.finalisee_le) as derniere"
                        + " from " + s + "vente v join " + s + "caisse c on c.id = v.caisse_id"
                        + " left join " + s + "paiement p on p.vente_id = v.id"
                        + " where v.caissier_id = :u and v.statut = 'VALIDEE'"
                        + " and v.finalisee_le >= :debut and v.finalisee_le < :fin"
                        + " group by v.session_caisse_id, c.nom order by derniere",
                periode(utilisateurId, debut, fin),
                (r, i) -> new VentesJour(
                        r.getLong("session_caisse_id"),
                        r.getString("nom"),
                        r.getInt("tickets"),
                        r.getBigDecimal("especes"),
                        r.getBigDecimal("carte"),
                        VersementsJdbc.instant(r, "derniere")));
    }

    public List<VersementJour> versements(long utilisateurId, Instant debut, Instant fin) {
        return jdbc.query(
                "select v.id, r.type = 'PRINCIPALE' as vers_principale, v.montant, v.statut, v.motif, v.cloture,"
                        + " v.auto_valide, trim(uc.prenom || ' ' || uc.nom) as cree_par, v.cree_le,"
                        + " trim(ut.prenom || ' ' || ut.nom) as traite_par, v.traite_le, ca.nom as caisse"
                        + " from " + s + "versement v"
                        + " join " + s + "compte_tresorerie e on e.id = v.emetteur_id"
                        + " join " + s + "compte_tresorerie r on r.id = v.recepteur_id"
                        + " join " + s + "utilisateur uc on uc.id = v.cree_par"
                        + " left join " + s + "utilisateur ut on ut.id = v.traite_par"
                        + " left join " + s + "session_caisse sc on sc.id = coalesce(v.session_emission_id,"
                        + " v.session_reception_id)"
                        + " left join " + s + "caisse ca on ca.id = sc.caisse_id"
                        + " where :u in (e.utilisateur_id, r.utilisateur_id)"
                        + " and v.cree_le >= :debut and v.cree_le < :fin order by v.cree_le, v.id",
                periode(utilisateurId, debut, fin),
                (r, i) -> new VersementJour(
                        r.getLong("id"),
                        r.getBoolean("vers_principale"),
                        r.getBigDecimal("montant"),
                        StatutVersement.valueOf(r.getString("statut")),
                        r.getString("motif"),
                        r.getBoolean("cloture"),
                        r.getBoolean("auto_valide"),
                        r.getString("cree_par"),
                        VersementsJdbc.instant(r, "cree_le"),
                        r.getString("traite_par"),
                        VersementsJdbc.instant(r, "traite_le"),
                        r.getString("caisse")));
    }

    public List<AjustementJour> ajustements(long utilisateurId, Instant debut, Instant fin) {
        return jdbc.query(
                "select o.montant, o.motif, trim(u.prenom || ' ' || u.nom) as auteur, o.cree_le"
                        + " from " + s + "operation_tresorerie o join " + s + "utilisateur u on u.id = o.auteur_id"
                        + " where o.caissier_id = :u and o.type = 'AJUSTEMENT'"
                        + " and o.cree_le >= :debut and o.cree_le < :fin order by o.cree_le, o.id",
                periode(utilisateurId, debut, fin),
                (r, i) -> new AjustementJour(
                        r.getBigDecimal("montant"),
                        r.getString("motif"),
                        r.getString("auteur"),
                        VersementsJdbc.instant(r, "cree_le")));
    }

    /** Solde du caissier juste après sa dernière opération avant {@code avant} ; vide s'il n'en a pas. */
    public Optional<BigDecimal> soldeAvant(long utilisateurId, Instant avant) {
        return jdbc
                .query(
                        "select solde_caissier_apres from " + s + "operation_tresorerie where caissier_id = :u"
                                + " and cree_le < :avant order by cree_le desc, id desc limit 1",
                        new MapSqlParameterSource("u", utilisateurId).addValue("avant", Timestamp.from(avant)),
                        (r, i) -> r.getBigDecimal("solde_caissier_apres"))
                .stream()
                .findFirst();
    }

    private static MapSqlParameterSource periode(long utilisateurId, Instant debut, Instant fin) {
        return new MapSqlParameterSource("u", utilisateurId)
                .addValue("debut", Timestamp.from(debut))
                .addValue("fin", Timestamp.from(fin));
    }
}
