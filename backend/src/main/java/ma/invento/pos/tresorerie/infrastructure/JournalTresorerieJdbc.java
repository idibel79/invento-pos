package ma.invento.pos.tresorerie.infrastructure;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.invento.pos.tresorerie.domain.StatutVersement;
import ma.invento.pos.tresorerie.domain.TypeCompte;
import ma.invento.pos.tresorerie.domain.TypeOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Journal de la caisse principale : une ligne par mouvement de solde, en ajout seul. La lecture n'en
 * présente qu'une par versement.
 */
@Component
public class JournalTresorerieJdbc {

    public record NouvelleOperation(
            TypeOperation type,
            long compteId,
            BigDecimal montant,
            Long caissierId,
            Long caisseId,
            Long sessionId,
            Long versementId,
            BigDecimal ecartSession,
            String motif,
            BigDecimal soldePrincipaleApres,
            BigDecimal soldeCaissierApres,
            long auteurId,
            UUID cle,
            Instant creeLe) {}

    /** Filtre du journal ; un critère null ne filtre pas ({@code types} vide : tous les types). */
    public record Filtre(
            Instant debut,
            Instant fin,
            Long caisseId,
            Long utilisateurId,
            FiltreStatut statut,
            Set<TypeOperation> types) {}

    public enum FiltreStatut {
        EN_TRANSIT,
        VALIDE,
        AUTO_VALIDE,
        REFUSE,
        ANNULE
    }

    /**
     * Ligne du journal. {@code montant} est signé du point de vue du compte mouvementé ({@code compte}) ;
     * {@code statut} est celui du versement lié (absent pour une opération immédiate, validée d'office).
     */
    public record Operation(
            Long id,
            Instant le,
            TypeOperation type,
            TypeCompte compte,
            BigDecimal montant,
            @Schema(nullable = true) Long caissierId,
            @Schema(nullable = true) String caissier,
            @Schema(nullable = true) String caisse,
            String auteur,
            @Schema(nullable = true) BigDecimal ecartSession,
            @Schema(nullable = true) Long versementId,
            @Schema(nullable = true) Boolean versLaPrincipale,
            @Schema(nullable = true) StatutVersement statut,
            boolean autoValide,
            boolean cloture,
            @Schema(nullable = true) String motif,
            BigDecimal soldePrincipaleApres,
            @Schema(nullable = true) BigDecimal soldeCaissierApres,

            @Schema(nullable = true, description = "Ventes de la session, sur un versement de clôture")
            BigDecimal ventesSession) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final String s;
    private final String depuis;

    JournalTresorerieJdbc(NamedParameterJdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        this.jdbc = jdbc;
        // Schéma issu de la configuration, jamais d'une saisie.
        this.s = schema + ".";
        this.depuis = " from " + s + "operation_tresorerie o"
                + " join " + s + "compte_tresorerie c on c.id = o.compte_id"
                + " join " + s + "utilisateur ua on ua.id = o.auteur_id"
                + " left join " + s + "utilisateur uc on uc.id = o.caissier_id"
                + " left join " + s + "caisse ca on ca.id = o.caisse_id"
                + " left join " + s + "versement v on v.id = o.versement_id"
                + " left join " + s + "compte_tresorerie rc on rc.id = v.recepteur_id"
                // Dernier mouvement du versement après l'envoi (réception, refus ou annulation).
                + " left join lateral (select x.solde_principale_apres, x.solde_caissier_apres from " + s
                + "operation_tresorerie x where x.versement_id = o.versement_id and x.type <> 'VERSEMENT_ENVOYE'"
                + " order by x.id desc limit 1) f on true"
                // Une ligne par versement (son envoi) : réception, refus et annulation n'en changent que le
                // statut et les soldes. La clôture de session n'est ni un versement ni une entrée d'argent.
                + " where o.type in ('ALIMENTATION', 'RETRAIT', 'VERSEMENT_ENVOYE', 'AJUSTEMENT')";
    }

    public void inserer(NouvelleOperation o) {
        jdbc.update(
                "insert into " + s + "operation_tresorerie (type, compte_id, montant, caissier_id, caisse_id,"
                        + " session_caisse_id, versement_id, ecart_session, motif, solde_principale_apres,"
                        + " solde_caissier_apres, auteur_id, cle_idempotence, cree_le)"
                        + " values (:type, :compte, :montant, :caissier, :caisse, :session, :versement, :ecart, :motif,"
                        + " :soldePrincipale, :soldeCaissier, :auteur, :cle, :le)",
                new MapSqlParameterSource("type", o.type().name())
                        .addValue("compte", o.compteId())
                        .addValue("montant", o.montant())
                        .addValue("caissier", o.caissierId())
                        .addValue("caisse", o.caisseId())
                        .addValue("session", o.sessionId())
                        .addValue("versement", o.versementId())
                        .addValue("ecart", o.ecartSession())
                        .addValue("motif", o.motif())
                        .addValue("soldePrincipale", o.soldePrincipaleApres())
                        .addValue("soldeCaissier", o.soldeCaissierApres())
                        .addValue("auteur", o.auteurId())
                        .addValue("cle", o.cle())
                        .addValue("le", Timestamp.from(o.creeLe())));
    }

    /** Une opération a déjà été enregistrée avec cette clé (double envoi). */
    public boolean existeCle(UUID cle) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from " + s + "operation_tresorerie where cle_idempotence = :cle)",
                new MapSqlParameterSource("cle", cle),
                Boolean.class));
    }

    public List<Operation> lister(Filtre filtre, boolean croissant, int page, int taille) {
        String ordre = croissant ? "asc" : "desc";
        // Montant d'un versement signé du point de vue de la caisse principale ; soldes après son dernier
        // mouvement (réception, refus ou annulation), sinon après l'envoi.
        return jdbc.query(
                "select o.id, o.cree_le, o.type, c.type as compte,"
                        + " case when v.id is null then o.montant when rc.type = 'PRINCIPALE' then v.montant"
                        + " else -v.montant end as montant, o.caissier_id,"
                        + " trim(uc.prenom || ' ' || uc.nom) as caissier, ca.nom as caisse,"
                        + " trim(ua.prenom || ' ' || ua.nom) as auteur, o.ecart_session, o.versement_id,"
                        + " case when v.id is null then null else rc.type = 'PRINCIPALE' end as vers_principale,"
                        + " v.statut, coalesce(v.auto_valide, false) as auto_valide,"
                        + " coalesce(v.cloture, false) as cloture, coalesce(v.motif, o.motif) as motif,"
                        + " coalesce(f.solde_principale_apres, o.solde_principale_apres) as solde_principale_apres,"
                        + " coalesce(f.solde_caissier_apres, o.solde_caissier_apres) as solde_caissier_apres,"
                        // Ventes de la session : crédits de clôture, hors excédent du tiroir.
                        + " case when v.cloture then (select sum(x.montant - greatest(coalesce(x.ecart_session, 0), 0))"
                        + " from " + s + "operation_tresorerie x where x.session_caisse_id = o.session_caisse_id"
                        + " and x.type = 'CLOTURE_SESSION') end as ventes_session"
                        + depuis + conditions(filtre)
                        + " order by o.cree_le " + ordre + ", o.id " + ordre + " limit :taille offset :decalage",
                parametres(filtre).addValue("taille", taille).addValue("decalage", (long) page * taille),
                (r, i) -> versOperation(r));
    }

    public long compter(Filtre filtre) {
        Long total =
                jdbc.queryForObject("select count(*)" + depuis + conditions(filtre), parametres(filtre), Long.class);
        return total == null ? 0 : total;
    }

    /** Conditions fixes, seules les valeurs varient. */
    private static String conditions(Filtre f) {
        String statut = f.statut() == null
                ? ""
                : switch (f.statut()) {
                    case VALIDE -> " and (v.id is null or (v.statut = 'VALIDE' and not v.auto_valide))";
                    case AUTO_VALIDE -> " and v.auto_valide";
                    default -> " and v.statut = :statut";
                };
        return (f.types().isEmpty() ? "" : " and o.type in (:types)")
                + (f.debut() == null ? "" : " and o.cree_le >= :debut")
                + (f.fin() == null ? "" : " and o.cree_le < :fin")
                + (f.caisseId() == null ? "" : " and o.caisse_id = :caisseId")
                + (f.utilisateurId() == null
                        ? ""
                        : " and (o.caissier_id = :utilisateurId or (o.caissier_id is null and o.auteur_id = :utilisateurId))")
                + statut;
    }

    private static MapSqlParameterSource parametres(Filtre f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (f.debut() != null) p.addValue("debut", Timestamp.from(f.debut()));
        if (f.fin() != null) p.addValue("fin", Timestamp.from(f.fin()));
        if (f.caisseId() != null) p.addValue("caisseId", f.caisseId());
        if (f.utilisateurId() != null) p.addValue("utilisateurId", f.utilisateurId());
        if (f.statut() != null) p.addValue("statut", f.statut().name());
        if (!f.types().isEmpty())
            p.addValue("types", f.types().stream().map(Enum::name).toList());
        return p;
    }

    private static Operation versOperation(ResultSet r) throws SQLException {
        String statut = r.getString("statut");
        return new Operation(
                r.getLong("id"),
                VersementsJdbc.instant(r, "cree_le"),
                TypeOperation.valueOf(r.getString("type")),
                TypeCompte.valueOf(r.getString("compte")),
                r.getBigDecimal("montant"),
                r.getObject("caissier_id", Long.class),
                r.getString("caissier"),
                r.getString("caisse"),
                r.getString("auteur"),
                r.getBigDecimal("ecart_session"),
                r.getObject("versement_id", Long.class),
                r.getObject("vers_principale", Boolean.class),
                statut == null ? null : StatutVersement.valueOf(statut),
                r.getBoolean("auto_valide"),
                r.getBoolean("cloture"),
                r.getString("motif"),
                r.getBigDecimal("solde_principale_apres"),
                r.getBigDecimal("solde_caissier_apres"),
                r.getBigDecimal("ventes_session"));
    }
}
