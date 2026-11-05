package ma.invento.pos.tresorerie.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ma.invento.pos.tresorerie.domain.StatutVersement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Versements entre la caisse principale et les caissiers. */
@Component
public class VersementsJdbc {

    /**
     * Versement avec le titulaire des deux comptes ; {@code caissierId} est l'utilisateur du côté
     * caissier, {@code versLaPrincipale} le sens.
     */
    public record Versement(
            long id,
            long emetteurId,
            long recepteurId,
            boolean versLaPrincipale,
            long caissierId,
            String caissier,
            BigDecimal montant,
            StatutVersement statut,
            String motif,
            Long sessionEmissionId,
            Long sessionReceptionId,
            boolean cloture,
            boolean autoValide,
            long creePar,
            Instant creeLe,
            Instant traiteLe) {}

    public record NouveauVersement(
            long emetteurId,
            long recepteurId,
            BigDecimal montant,
            String motif,
            Long sessionEmissionId,
            boolean cloture,
            UUID cle,
            long creePar,
            Instant creeLe) {}

    /** Versements en transit d'un compte : reçus (à valider par lui) et envoyés (à valider par l'autre). */
    public record EnTransit(int recusNombre, BigDecimal recusMontant, int envoyesNombre, BigDecimal envoyesMontant) {
        public static final EnTransit AUCUN = new EnTransit(0, BigDecimal.ZERO, 0, BigDecimal.ZERO);
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final String s;
    private final String selection;

    VersementsJdbc(NamedParameterJdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        this.jdbc = jdbc;
        // Schéma issu de la configuration, jamais d'une saisie.
        this.s = schema + ".";
        this.selection = "select v.id, v.emetteur_id, v.recepteur_id, r.type = 'PRINCIPALE' as vers_principale,"
                + " u.id as caissier_id, trim(u.prenom || ' ' || u.nom) as caissier, v.montant, v.statut, v.motif,"
                + " v.session_emission_id, v.session_reception_id, v.cloture, v.auto_valide, v.cree_par, v.cree_le,"
                + " v.traite_le"
                + " from " + s + "versement v"
                + " join " + s + "compte_tresorerie e on e.id = v.emetteur_id"
                + " join " + s + "compte_tresorerie r on r.id = v.recepteur_id"
                + " join " + s + "utilisateur u on u.id = coalesce(e.utilisateur_id, r.utilisateur_id)";
    }

    public long inserer(NouveauVersement v) {
        return jdbc.queryForObject(
                "insert into " + s + "versement (emetteur_id, recepteur_id, montant, statut, motif,"
                        + " session_emission_id, cloture, cle_idempotence, cree_par, cree_le)"
                        + " values (:emetteur, :recepteur, :montant, 'EN_TRANSIT', :motif, :session, :cloture, :cle,"
                        + " :creePar, :creeLe) returning id",
                new MapSqlParameterSource("emetteur", v.emetteurId())
                        .addValue("recepteur", v.recepteurId())
                        .addValue("montant", v.montant())
                        .addValue("motif", v.motif())
                        .addValue("session", v.sessionEmissionId())
                        .addValue("cloture", v.cloture())
                        .addValue("cle", v.cle())
                        .addValue("creePar", v.creePar())
                        .addValue("creeLe", Timestamp.from(v.creeLe())),
                Long.class);
    }

    public Optional<Versement> parId(long id) {
        return premier(selection + " where v.id = :id", new MapSqlParameterSource("id", id));
    }

    /** Verrou de ligne : validation, refus et annulation d'un même versement ne se croisent pas. */
    public Optional<Versement> parIdPourMaj(long id) {
        return premier(selection + " where v.id = :id for update of v", new MapSqlParameterSource("id", id));
    }

    public Optional<Versement> parCle(UUID cle) {
        return premier(selection + " where v.cle_idempotence = :cle", new MapSqlParameterSource("cle", cle));
    }

    /**
     * Passe un versement en transit à son statut final ; faux s'il a déjà été traité entre-temps.
     * {@code sessionReception} : session ouverte du caissier qui reçoit l'argent dans son tiroir.
     */
    public boolean traiter(
            long id,
            StatutVersement statut,
            String motif,
            boolean autoValide,
            Long sessionReception,
            long par,
            Instant le) {
        return jdbc.update(
                        "update " + s + "versement set statut = :statut, motif = coalesce(:motif, motif),"
                                + " auto_valide = :auto, session_reception_id = :session, traite_par = :par,"
                                + " traite_le = :le where id = :id and statut = 'EN_TRANSIT'",
                        new MapSqlParameterSource("id", id)
                                .addValue("statut", statut.name())
                                .addValue("motif", motif)
                                .addValue("auto", autoValide)
                                .addValue("session", sessionReception)
                                .addValue("par", par)
                                .addValue("le", Timestamp.from(le)))
                == 1;
    }

    /** Versements en transit reçus par un compte, du plus ancien au plus récent. */
    public List<Versement> enTransitVers(long compteId) {
        return jdbc.query(
                selection + " where v.recepteur_id = :c and v.statut = 'EN_TRANSIT' order by v.cree_le, v.id",
                new MapSqlParameterSource("c", compteId),
                (r, i) -> versVersement(r));
    }

    /** Versements d'un compte (dans les deux sens) créés depuis {@code depuis}, ou encore en transit. */
    public List<Versement> recentsDuCompte(long compteId, Instant depuis) {
        return jdbc.query(
                selection + " where (v.emetteur_id = :c or v.recepteur_id = :c)"
                        + " and (v.cree_le >= :depuis or v.statut = 'EN_TRANSIT')"
                        + " order by v.cree_le desc, v.id desc",
                new MapSqlParameterSource("c", compteId).addValue("depuis", Timestamp.from(depuis)),
                (r, i) -> versVersement(r));
    }

    public boolean existeEnTransit(long compteId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from " + s + "versement where statut = 'EN_TRANSIT'"
                        + " and (emetteur_id = :c or recepteur_id = :c))",
                new MapSqlParameterSource("c", compteId),
                Boolean.class));
    }

    /**
     * Argent entré dans le tiroir (versements validés pendant la session) et sorti (versements envoyés
     * pendant la session et ni refusés ni annulés), hors versement de clôture.
     */
    public BigDecimal[] recuEtVerse(long sessionId) {
        return jdbc.queryForObject(
                "select coalesce(sum(montant) filter (where session_reception_id = :s and statut = 'VALIDE'), 0)"
                        + " as recu,"
                        + " coalesce(sum(montant) filter (where session_emission_id = :s and not cloture"
                        + " and statut in ('EN_TRANSIT', 'VALIDE')), 0) as verse"
                        + " from " + s + "versement where session_reception_id = :s or session_emission_id = :s",
                new MapSqlParameterSource("s", sessionId),
                (r, i) -> new BigDecimal[] {r.getBigDecimal("recu"), r.getBigDecimal("verse")});
    }

    /** Versements en transit par compte de caissier (utilisateur), dans les deux sens. */
    public Map<Long, EnTransit> enTransitParCaissier() {
        Map<Long, EnTransit> resultat = new HashMap<>();
        jdbc.query(
                "select c.utilisateur_id,"
                        + " count(*) filter (where v.recepteur_id = c.id) as recus_nombre,"
                        + " coalesce(sum(v.montant) filter (where v.recepteur_id = c.id), 0) as recus_montant,"
                        + " count(*) filter (where v.emetteur_id = c.id) as envoyes_nombre,"
                        + " coalesce(sum(v.montant) filter (where v.emetteur_id = c.id), 0) as envoyes_montant"
                        + " from " + s + "versement v"
                        + " join " + s + "compte_tresorerie c on c.type = 'CAISSIER'"
                        + " and (c.id = v.emetteur_id or c.id = v.recepteur_id)"
                        + " where v.statut = 'EN_TRANSIT' group by c.utilisateur_id",
                r -> {
                    resultat.put(
                            r.getLong("utilisateur_id"),
                            new EnTransit(
                                    r.getInt("recus_nombre"),
                                    r.getBigDecimal("recus_montant"),
                                    r.getInt("envoyes_nombre"),
                                    r.getBigDecimal("envoyes_montant")));
                });
        return resultat;
    }

    private Optional<Versement> premier(String sql, MapSqlParameterSource parametres) {
        return jdbc.query(sql, parametres, (r, i) -> versVersement(r)).stream().findFirst();
    }

    private static Versement versVersement(ResultSet r) throws SQLException {
        return new Versement(
                r.getLong("id"),
                r.getLong("emetteur_id"),
                r.getLong("recepteur_id"),
                r.getBoolean("vers_principale"),
                r.getLong("caissier_id"),
                r.getString("caissier"),
                r.getBigDecimal("montant"),
                StatutVersement.valueOf(r.getString("statut")),
                r.getString("motif"),
                r.getObject("session_emission_id", Long.class),
                r.getObject("session_reception_id", Long.class),
                r.getBoolean("cloture"),
                r.getBoolean("auto_valide"),
                r.getLong("cree_par"),
                instant(r, "cree_le"),
                instant(r, "traite_le"));
    }

    static Instant instant(ResultSet r, String colonne) throws SQLException {
        OffsetDateTime valeur = r.getObject(colonne, OffsetDateTime.class);
        return valeur == null ? null : valeur.toInstant();
    }
}
