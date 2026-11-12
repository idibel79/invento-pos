package ma.invento.pos.vente.infrastructure;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import ma.invento.pos.vente.domain.ModePaiement;
import ma.invento.pos.vente.domain.TypeRemise;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lecture de l'historique des ventes validées, consulté par l'admin : liste paginée d'une période, ses
 * totaux et le détail d'une vente. Lu en SQL (filtres, sommes et pagination côté base) pour ne charger
 * ni les ventes ni leurs lignes en entités.
 */
@Component
public class HistoriqueVentesRequete {

    /**
     * Vente telle que la liste l'affiche : remises par portée (lignes, ticket) et leur total, personne qui a
     * autorisé la remise ; {@code mode} est absent pour une vente à 0 DH, {@code montantRecu} et {@code rendu}
     * n'existent qu'en espèces.
     */
    public record VenteHistorique(
            Long id,
            String numero,
            Instant le,
            String caisse,
            String vendeur,
            int articles,
            BigDecimal remisesLignes,
            BigDecimal remiseTicket,
            BigDecimal remises,
            @Schema(nullable = true) String autorisePar,
            BigDecimal totalTtc,
            @Schema(nullable = true) ModePaiement mode,
            @Schema(nullable = true) BigDecimal montantRecu,
            @Schema(nullable = true) BigDecimal rendu,
            BigDecimal montantCarte,
            @Schema(nullable = true) String client) {}

    /**
     * Totaux de la sélection : nombre de ventes, chiffre d'affaires TTC, remises et répartition des paiements
     * ({@code credit} : ventes à crédit, sur la dette des clients).
     */
    public record ResumeVentes(
            long nombre,
            BigDecimal totalTtc,
            BigDecimal remises,
            BigDecimal especes,
            BigDecimal carte,
            BigDecimal credit) {}

    /**
     * Sélection : ventes validées dans [debut, fin[, d'une caisse, d'un vendeur et d'un client (partie de son
     * nom, ou client précis de l'annuaire par son identifiant) ; un critère null ne filtre pas.
     */
    public record Filtre(Instant debut, Instant fin, Long caisseId, Long vendeurId, String client, Long clientId) {}

    public record LigneHistorique(
            String designation,
            int quantite,
            BigDecimal prixUnitaire,
            BigDecimal tauxTva,
            @Schema(nullable = true) TypeRemise remiseType,
            @Schema(nullable = true) BigDecimal remiseValeur,
            BigDecimal remiseMontant,
            BigDecimal montantBrut,
            BigDecimal montantNet) {}

    /** Détail d'une vente validée : lignes, remise du ticket, totaux et paiement tels qu'enregistrés. */
    public record DetailVente(
            Long id,
            String numero,
            Instant le,
            String caisse,
            String vendeur,
            @Schema(nullable = true) String autorisePar,
            List<LigneHistorique> lignes,
            BigDecimal sousTotal,
            BigDecimal remisesLignes,
            @Schema(nullable = true) TypeRemise remiseTicketType,
            @Schema(nullable = true) BigDecimal remiseTicketValeur,
            @Schema(nullable = true) BigDecimal remiseTicketMontant,
            @Schema(nullable = true) String motifRemise,
            BigDecimal totalHt,
            BigDecimal totalTva,
            BigDecimal totalTtc,
            @Schema(nullable = true) ModePaiement mode,
            @Schema(nullable = true) BigDecimal montantRecu,
            @Schema(nullable = true) BigDecimal rendu,
            BigDecimal montantCarte,
            @Schema(nullable = true) String client) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final String ventes;
    private final String detail;
    private final String lignes;
    private final String articles;
    private final String remisesLignes;
    private final String remises;
    private final String colonnesPaiement;

    HistoriqueVentesRequete(NamedParameterJdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        this.jdbc = jdbc;
        // Schéma issu de la configuration (jamais d'une saisie), comme pour la numérotation des ventes.
        String s = schema + ".";
        this.articles = "(select coalesce(sum(lv.quantite), 0) from " + s + "ligne_vente lv where lv.vente_id = v.id)";
        this.remisesLignes = "(select coalesce(sum(lv.remise_montant), 0) from " + s + "ligne_vente lv"
                + " where lv.vente_id = v.id)";
        this.remises = remisesLignes + " + coalesce(v.remise_ticket_montant, 0)";
        // Une vente validée a au plus un paiement en espèces et un par carte (les deux : paiement mixte ; aucun à 0
        // DH).
        String paiement = " left join " + s + "paiement p on p.vente_id = v.id and p.mode = 'ESPECES'" + " left join "
                + s + "paiement pk on pk.vente_id = v.id and pk.mode = 'CARTE'" + " left join " + s
                + "paiement pc on pc.vente_id = v.id and pc.mode = 'CREDIT'";
        this.colonnesPaiement = ", coalesce(p.mode, pk.mode, pc.mode) as mode, p.montant_recu, p.rendu,"
                + " coalesce(pk.montant, 0) as montant_carte, v.client_nom";
        this.ventes = " from " + s + "vente v"
                + " join " + s + "caisse c on c.id = v.caisse_id"
                + " join " + s + "utilisateur uv on uv.id = v.caissier_id"
                + " left join " + s + "utilisateur ua on ua.id = v.validee_par"
                + paiement
                + " where v.statut = 'VALIDEE'";
        this.detail = "select v.id, v.numero, v.finalisee_le, c.nom as caisse,"
                + " uv.prenom || ' ' || uv.nom as vendeur, ua.prenom || ' ' || ua.nom as autorise_par,"
                + " v.remise_ticket_type, v.remise_ticket_valeur, v.remise_ticket_montant, v.motif_remise,"
                + " v.total_ht, v.total_tva, v.total_ttc" + colonnesPaiement
                + " from " + s + "vente v"
                + " join " + s + "caisse c on c.id = v.caisse_id"
                + " join " + s + "utilisateur uv on uv.id = v.caissier_id"
                + " left join " + s + "utilisateur ua on ua.id = v.validee_par"
                + paiement
                + " where v.statut = 'VALIDEE' and v.id = :id";
        this.lignes = "select lv.designation, lv.quantite, lv.prix_unitaire, lv.taux_tva, lv.remise_type,"
                + " lv.remise_valeur, coalesce(lv.remise_montant, 0) as remise_montant,"
                + " lv.prix_unitaire * lv.quantite as brut"
                + " from " + s + "ligne_vente lv"
                + " where lv.vente_id = :id order by lv.id";
    }

    public List<VenteHistorique> lister(Filtre filtre, int page, int taille) {
        MapSqlParameterSource parametres =
                parametres(filtre).addValue("taille", taille).addValue("decalage", (long) page * taille);
        return jdbc.query(
                "select v.id, v.numero, v.finalisee_le, c.nom as caisse, uv.prenom || ' ' || uv.nom as vendeur,"
                        + " " + articles + " as articles, " + remisesLignes + " as remises_lignes,"
                        + " coalesce(v.remise_ticket_montant, 0) as remise_ticket, " + remises + " as remises,"
                        + " ua.prenom || ' ' || ua.nom as autorise_par, v.total_ttc"
                        + colonnesPaiement
                        + ventes + conditions(filtre)
                        + " order by v.finalisee_le desc, v.id desc limit :taille offset :decalage",
                parametres,
                HistoriqueVentesRequete::versVente);
    }

    public ResumeVentes resumer(Filtre filtre) {
        return jdbc.queryForObject(
                "select count(*) as nombre, coalesce(sum(r.total_ttc), 0) as total,"
                        + " coalesce(sum(r.remises), 0) as remises,"
                        + " coalesce(sum(r.especes), 0) as especes,"
                        + " coalesce(sum(r.carte), 0) as carte,"
                        + " coalesce(sum(r.credit), 0) as credit"
                        + " from (select v.total_ttc, " + remises
                        + " as remises, p.montant as especes, pk.montant as carte, pc.montant as credit"
                        + ventes + conditions(filtre) + ") r",
                parametres(filtre),
                (r, i) -> new ResumeVentes(
                        r.getLong("nombre"),
                        r.getBigDecimal("total"),
                        r.getBigDecimal("remises"),
                        r.getBigDecimal("especes"),
                        r.getBigDecimal("carte"),
                        r.getBigDecimal("credit")));
    }

    /**
     * Vente validée et ses lignes ; vide si elle n'existe pas, n'est pas validée ou, quand {@code vendeurId}
     * est donné, n'a pas été faite par ce vendeur.
     */
    public Optional<DetailVente> detail(long id, Long vendeurId) {
        MapSqlParameterSource parametres = new MapSqlParameterSource("id", id);
        String requete = detail;
        if (vendeurId != null) {
            requete += " and v.caissier_id = :vendeurId";
            parametres.addValue("vendeurId", vendeurId);
        }
        return jdbc.query(requete, parametres, (r, i) -> versDetail(r)).stream()
                .findFirst()
                .map(entete -> entete.apply(jdbc.query(lignes, parametres, HistoriqueVentesRequete::versLigne)));
    }

    /** Conditions fixes, seules les valeurs varient. */
    private static String conditions(Filtre filtre) {
        return (filtre.debut() == null ? "" : " and v.finalisee_le >= :debut")
                + (filtre.fin() == null ? "" : " and v.finalisee_le < :fin")
                + (filtre.caisseId() == null ? "" : " and v.caisse_id = :caisseId")
                + (filtre.vendeurId() == null ? "" : " and v.caissier_id = :vendeurId")
                + (RechercheClient.motif(filtre.client()) == null ? "" : RechercheClient.condition("v.client_nom"))
                + (filtre.clientId() == null ? "" : " and v.client_id = :clientId");
    }

    private static MapSqlParameterSource parametres(Filtre filtre) {
        MapSqlParameterSource parametres = new MapSqlParameterSource();
        if (filtre.debut() != null) parametres.addValue("debut", Timestamp.from(filtre.debut()));
        if (filtre.fin() != null) parametres.addValue("fin", Timestamp.from(filtre.fin()));
        if (filtre.caisseId() != null) parametres.addValue("caisseId", filtre.caisseId());
        if (filtre.vendeurId() != null) parametres.addValue("vendeurId", filtre.vendeurId());
        parametres.addValue("client", RechercheClient.motif(filtre.client()));
        if (filtre.clientId() != null) parametres.addValue("clientId", filtre.clientId());
        return parametres;
    }

    private static VenteHistorique versVente(ResultSet r, int ligne) throws SQLException {
        return new VenteHistorique(
                r.getLong("id"),
                r.getString("numero"),
                r.getTimestamp("finalisee_le").toInstant(),
                r.getString("caisse"),
                r.getString("vendeur"),
                r.getInt("articles"),
                r.getBigDecimal("remises_lignes"),
                r.getBigDecimal("remise_ticket"),
                r.getBigDecimal("remises"),
                r.getString("autorise_par"),
                r.getBigDecimal("total_ttc"),
                mode(r),
                r.getBigDecimal("montant_recu"),
                r.getBigDecimal("rendu"),
                r.getBigDecimal("montant_carte"),
                r.getString("client_nom"));
    }

    private static LigneHistorique versLigne(ResultSet r, int ligne) throws SQLException {
        BigDecimal brut = r.getBigDecimal("brut");
        BigDecimal remise = r.getBigDecimal("remise_montant");
        String type = r.getString("remise_type");
        return new LigneHistorique(
                r.getString("designation"),
                r.getInt("quantite"),
                r.getBigDecimal("prix_unitaire"),
                r.getBigDecimal("taux_tva"),
                type == null ? null : TypeRemise.valueOf(type),
                r.getBigDecimal("remise_valeur"),
                remise,
                brut,
                brut.subtract(remise));
    }

    /** En-tête lu dans la ligne courante, complété par les lignes de la vente lues ensuite. */
    private static Function<List<LigneHistorique>, DetailVente> versDetail(ResultSet r) throws SQLException {
        long id = r.getLong("id");
        String numero = r.getString("numero");
        Instant le = r.getTimestamp("finalisee_le").toInstant();
        String caisse = r.getString("caisse");
        String vendeur = r.getString("vendeur");
        String autorisePar = r.getString("autorise_par");
        String type = r.getString("remise_ticket_type");
        BigDecimal remiseTicketValeur = r.getBigDecimal("remise_ticket_valeur");
        BigDecimal remiseTicketMontant = r.getBigDecimal("remise_ticket_montant");
        String motif = r.getString("motif_remise");
        BigDecimal totalHt = r.getBigDecimal("total_ht");
        BigDecimal totalTva = r.getBigDecimal("total_tva");
        BigDecimal totalTtc = r.getBigDecimal("total_ttc");
        ModePaiement mode = mode(r);
        BigDecimal montantRecu = r.getBigDecimal("montant_recu");
        BigDecimal rendu = r.getBigDecimal("rendu");
        BigDecimal montantCarte = r.getBigDecimal("montant_carte");
        String client = r.getString("client_nom");
        return lignes -> new DetailVente(
                id,
                numero,
                le,
                caisse,
                vendeur,
                autorisePar,
                lignes,
                lignes.stream().map(LigneHistorique::montantBrut).reduce(BigDecimal.ZERO, BigDecimal::add),
                lignes.stream().map(LigneHistorique::remiseMontant).reduce(BigDecimal.ZERO, BigDecimal::add),
                type == null ? null : TypeRemise.valueOf(type),
                remiseTicketValeur,
                remiseTicketMontant,
                motif,
                totalHt,
                totalTva,
                totalTtc,
                mode,
                montantRecu,
                rendu,
                montantCarte,
                client);
    }

    private static ModePaiement mode(ResultSet r) throws SQLException {
        String mode = r.getString("mode");
        return mode == null ? null : ModePaiement.valueOf(mode);
    }
}
