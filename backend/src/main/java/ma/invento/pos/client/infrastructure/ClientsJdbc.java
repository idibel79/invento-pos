package ma.invento.pos.client.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ma.invento.pos.client.domain.TypeMouvementClient;
import ma.invento.pos.tresorerie.application.RemboursementsDettes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Clients et relevés. La dette ne change que par des mises à jour atomiques ; négative, c'est une avance
 * du client. Le relevé est en ajout seul, un remboursement annulé est seulement marqué.
 */
@Component
public class ClientsJdbc implements RemboursementsDettes {

    public record Client(
            long id,
            String nom,
            String telephone,
            String note,
            boolean actif,
            BigDecimal dette,
            BigDecimal plafond,
            Instant dernierMouvement) {}

    public record Mouvement(
            long id,
            long clientId,
            String client,
            TypeMouvementClient type,
            BigDecimal montant,
            BigDecimal especes,
            BigDecimal tpe,
            Long venteId,
            String venteNumero,
            Long remboursementId,
            boolean annule,
            long utilisateurId,
            String utilisateur,
            Long sessionId,
            boolean caissePrincipale,
            String motif,
            BigDecimal detteApres,
            Instant creeLe) {}

    public record NouveauMouvement(
            long clientId,
            TypeMouvementClient type,
            BigDecimal montant,
            BigDecimal especes,
            BigDecimal tpe,
            Long venteId,
            Long remboursementId,
            long utilisateurId,
            Long sessionId,
            boolean caissePrincipale,
            String motif,
            BigDecimal detteApres,
            UUID cle,
            Instant creeLe) {}

    /** Résultat d'une mise à jour de la dette : le nom du client et sa dette juste après. */
    public record Dette(String nom, BigDecimal apres) {}

    private static final int RELEVE_MAX = 300;
    private static final int LISTE_MAX = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final String s;

    ClientsJdbc(NamedParameterJdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        this.jdbc = jdbc;
        // Schéma issu de la configuration, jamais d'une saisie.
        this.s = schema + ".";
    }

    // ---------------------------------------------------------------- Annuaire

    /** Clients par nom ; {@code recherche} filtre sur le nom ou le téléphone. */
    public List<Client> lister(String recherche, boolean inactifs) {
        String motif = recherche == null || recherche.isBlank()
                ? null
                : "%"
                        + recherche
                                .strip()
                                .toLowerCase()
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_") + "%";
        return jdbc.query(
                selectClient() + " where (c.actif or :inactifs)"
                        + (motif == null
                                ? ""
                                : " and (lower(c.nom) like :motif or replace(coalesce(c.telephone, ''), ' ', '')"
                                        + " like replace(:motif, ' ', ''))")
                        + " order by lower(c.nom), c.id limit " + LISTE_MAX,
                new MapSqlParameterSource("inactifs", inactifs).addValue("motif", motif),
                (r, i) -> versClient(r));
    }

    public Optional<Client> parId(long id) {
        return jdbc.query(selectClient() + " where c.id = :id", Map.of("id", id), (r, i) -> versClient(r)).stream()
                .findFirst();
    }

    public long inserer(String nom, String telephone, String note, BigDecimal plafond, Instant creeLe) {
        return jdbc.queryForObject(
                "insert into " + s + "client (nom, telephone, note, plafond, cree_le)"
                        + " values (:nom, :tel, :note, :plafond, :le) returning id",
                new MapSqlParameterSource("nom", nom)
                        .addValue("tel", telephone)
                        .addValue("note", note)
                        .addValue("plafond", plafond, java.sql.Types.NUMERIC)
                        .addValue("le", Timestamp.from(creeLe)),
                Long.class);
    }

    public boolean modifier(long id, String nom, String telephone, String note, BigDecimal plafond, boolean actif) {
        return jdbc.update(
                        "update " + s + "client set nom = :nom, telephone = :tel, note = :note,"
                                + " plafond = :plafond, actif = :actif where id = :id",
                        new MapSqlParameterSource("id", id)
                                .addValue("nom", nom)
                                .addValue("tel", telephone)
                                .addValue("note", note)
                                .addValue("plafond", plafond, java.sql.Types.NUMERIC)
                                .addValue("actif", actif))
                == 1;
    }

    /** Un client actif porte déjà ce nom (casse ignorée), hors {@code saufId}. */
    public boolean nomActifPris(String nom, Long saufId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from " + s + "client where actif and lower(nom) = lower(:nom)"
                        + " and id <> coalesce(cast(:sauf as bigint), -1))",
                new MapSqlParameterSource("nom", nom).addValue("sauf", saufId),
                Boolean.class));
    }

    /** Le client a déjà acheté (vente, même payée comptant) ou a un relevé : il ne peut qu'être désactivé. */
    public boolean aUnHistorique(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from " + s + "mouvement_client where client_id = :id)"
                        + " or exists (select 1 from " + s + "vente where client_id = :id)",
                Map.of("id", id),
                Boolean.class));
    }

    public boolean supprimer(long id) {
        return jdbc.update("delete from " + s + "client where id = :id", Map.of("id", id)) == 1;
    }

    // ---------------------------------------------------------------- Dette

    /**
     * Ajoute à la dette d'un client actif, sans dépasser son plafond ; vide s'il n'existe pas, est désactivé
     * ou si le plafond serait dépassé.
     */
    public Optional<Dette> ajouterDette(long id, BigDecimal montant) {
        return jdbc
                .query(
                        "update " + s + "client set dette = dette + :m where id = :id and actif"
                                + " and (plafond is null or dette + :m <= plafond) returning nom, dette",
                        new MapSqlParameterSource("id", id).addValue("m", montant),
                        (r, i) -> new Dette(r.getString("nom"), r.getBigDecimal("dette")))
                .stream()
                .findFirst();
    }

    /** Remonte la dette (annulation d'un remboursement), client actif ou non. */
    public Dette remonterDette(long id, BigDecimal montant) {
        return jdbc.queryForObject(
                "update " + s + "client set dette = dette + :m where id = :id returning nom, dette",
                new MapSqlParameterSource("id", id).addValue("m", montant),
                (r, i) -> new Dette(r.getString("nom"), r.getBigDecimal("dette")));
    }

    /** Retire de la dette ; au-delà, le surplus devient une avance (dette négative). */
    public Dette retirerDette(long id, BigDecimal montant) {
        return jdbc.queryForObject(
                "update " + s + "client set dette = dette - :m where id = :id returning nom, dette",
                new MapSqlParameterSource("id", id).addValue("m", montant),
                (r, i) -> new Dette(r.getString("nom"), r.getBigDecimal("dette")));
    }

    // ---------------------------------------------------------------- Relevé

    public long insererMouvement(NouveauMouvement m) {
        return jdbc.queryForObject(
                "insert into " + s + "mouvement_client (client_id, type, montant, especes, tpe, vente_id,"
                        + " remboursement_id, utilisateur_id, session_caisse_id, caisse_principale, motif,"
                        + " dette_apres, cle_idempotence, cree_le) values (:client, :type, :montant, :especes,"
                        + " :tpe, :vente, :remboursement, :utilisateur, :session, :principale, :motif, :apres,"
                        + " :cle, :le) returning id",
                new MapSqlParameterSource("client", m.clientId())
                        .addValue("type", m.type().name())
                        .addValue("montant", m.montant())
                        .addValue("especes", m.especes())
                        .addValue("tpe", m.tpe())
                        .addValue("vente", m.venteId())
                        .addValue("remboursement", m.remboursementId())
                        .addValue("utilisateur", m.utilisateurId())
                        .addValue("session", m.sessionId())
                        .addValue("principale", m.caissePrincipale())
                        .addValue("motif", m.motif())
                        .addValue("apres", m.detteApres())
                        .addValue("cle", m.cle())
                        .addValue("le", Timestamp.from(m.creeLe())),
                Long.class);
    }

    /** Relevé du plus récent au plus ancien. */
    public List<Mouvement> releve(long clientId) {
        return jdbc.query(
                selectMouvement() + " where m.client_id = :id order by m.cree_le desc, m.id desc limit " + RELEVE_MAX,
                Map.of("id", clientId),
                (r, i) -> versMouvement(r));
    }

    public Optional<Mouvement> mouvement(long id) {
        return jdbc
                .query(selectMouvement() + " where m.id = :id", Map.of("id", id), (r, i) -> versMouvement(r))
                .stream()
                .findFirst();
    }

    public Optional<Mouvement> parCle(UUID cle) {
        return jdbc
                .query(
                        selectMouvement() + " where m.cle_idempotence = :cle",
                        new MapSqlParameterSource("cle", cle),
                        (r, i) -> versMouvement(r))
                .stream()
                .findFirst();
    }

    /** Marque un remboursement annulé ; faux s'il l'était déjà (deux annulations simultanées). */
    public boolean marquerAnnule(long remboursementId) {
        return jdbc.update(
                        "update " + s + "mouvement_client set annule = true where id = :id"
                                + " and type = 'REMBOURSEMENT' and not annule",
                        Map.of("id", remboursementId))
                == 1;
    }

    @Override
    public Montants pendantLaSession(long sessionId) {
        return jdbc.queryForObject(
                "select coalesce(sum(especes), 0) as especes, coalesce(sum(tpe), 0) as tpe from " + s
                        + "mouvement_client where session_caisse_id = :s and type = 'REMBOURSEMENT' and not annule",
                Map.of("s", sessionId),
                (r, i) -> new Montants(r.getBigDecimal("especes"), r.getBigDecimal("tpe")));
    }

    // ---------------------------------------------------------------- Lecture

    private String selectClient() {
        return "select c.id, c.nom, c.telephone, c.note, c.actif, c.dette, c.plafond,"
                + " (select max(m.cree_le) from " + s + "mouvement_client m where m.client_id = c.id) as dernier"
                + " from " + s + "client c";
    }

    private String selectMouvement() {
        return "select m.*, c.nom as client, v.numero as vente_numero,"
                + " trim(coalesce(u.prenom, '') || ' ' || coalesce(u.nom, '')) as utilisateur"
                + " from " + s + "mouvement_client m"
                + " join " + s + "client c on c.id = m.client_id"
                + " join " + s + "utilisateur u on u.id = m.utilisateur_id"
                + " left join " + s + "vente v on v.id = m.vente_id";
    }

    private static Client versClient(ResultSet r) throws SQLException {
        Timestamp dernier = r.getTimestamp("dernier");
        return new Client(
                r.getLong("id"),
                r.getString("nom"),
                r.getString("telephone"),
                r.getString("note"),
                r.getBoolean("actif"),
                r.getBigDecimal("dette"),
                r.getBigDecimal("plafond"),
                dernier == null ? null : dernier.toInstant());
    }

    private static Mouvement versMouvement(ResultSet r) throws SQLException {
        return new Mouvement(
                r.getLong("id"),
                r.getLong("client_id"),
                r.getString("client"),
                TypeMouvementClient.valueOf(r.getString("type")),
                r.getBigDecimal("montant"),
                r.getBigDecimal("especes"),
                r.getBigDecimal("tpe"),
                r.getObject("vente_id", Long.class),
                r.getString("vente_numero"),
                r.getObject("remboursement_id", Long.class),
                r.getBoolean("annule"),
                r.getLong("utilisateur_id"),
                r.getString("utilisateur"),
                r.getObject("session_caisse_id", Long.class),
                r.getBoolean("caisse_principale"),
                r.getString("motif"),
                r.getBigDecimal("dette_apres"),
                r.getTimestamp("cree_le").toInstant());
    }
}
