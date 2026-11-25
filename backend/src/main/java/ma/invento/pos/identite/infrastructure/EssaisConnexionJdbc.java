package ma.invento.pos.identite.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import ma.invento.pos.identite.domain.Utilisateur;
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
    private final String utilisateurs;

    EssaisConnexionJdbc(NamedParameterJdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        this.jdbc = jdbc;
        // Schéma issu de la configuration, jamais d'une saisie.
        this.limites = schema + ".limite_essai";
        this.inconnus = schema + ".essai_identifiant_inconnu";
        this.utilisateurs = schema + ".utilisateur";
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

    /**
     * Ajoute un mot de passe raté au compte. Un verrouillage terminé repart de zéro ; le
     * {@link Utilisateur#ESSAIS_MAX}e essai pose la fin du verrouillage.
     */
    public EssaisRates ajouterEssaiCompte(long utilisateurId, Instant maintenant) {
        return jdbc.queryForObject(
                "update " + utilisateurs + " e set essais_rates = " + nouveauxEssais("essais_rates")
                        + ", verrouille_jusqua = " + nouvelleFin("essais_rates")
                        + " where e.id = :id returning e.essais_rates, e.verrouille_jusqua",
                parametres(maintenant).addValue("id", utilisateurId),
                EssaisConnexionJdbc::lire);
    }

    /** Même règle pour un identifiant inconnu, qui reçoit ainsi les mêmes réponses qu'un vrai compte. */
    public EssaisRates ajouterEssaiInconnu(String identifiantHash, Instant maintenant) {
        return jdbc.queryForObject(
                "insert into " + inconnus + " as e (identifiant_hash, essais, mis_a_jour) values (:h, 1, :t)"
                        + " on conflict (identifiant_hash) do update set essais = " + nouveauxEssais("essais")
                        + ", verrouille_jusqua = " + nouvelleFin("essais")
                        + ", mis_a_jour = :t returning e.essais, e.verrouille_jusqua",
                parametres(maintenant).addValue("h", identifiantHash),
                EssaisConnexionJdbc::lire);
    }

    /** Pendant le verrouillage rien ne change ; une fois terminé, le compteur repart à un essai. */
    private static String nouveauxEssais(String essais) {
        return "case when e.verrouille_jusqua > :t then e." + essais + " when e.verrouille_jusqua <= :t then 1 else e."
                + essais + " + 1 end";
    }

    /** Fin du verrouillage gardée tant qu'il dure, posée au dernier essai permis, effacée ensuite. */
    private static String nouvelleFin(String essais) {
        return "case when e.verrouille_jusqua > :t then e.verrouille_jusqua"
                + " when e.verrouille_jusqua is null and e." + essais + " + 1 >= :max then :fin end";
    }

    private static MapSqlParameterSource parametres(Instant maintenant) {
        return new MapSqlParameterSource("t", Timestamp.from(maintenant))
                .addValue("fin", Timestamp.from(maintenant.plus(Utilisateur.DUREE_VERROUILLAGE)))
                .addValue("max", Utilisateur.ESSAIS_MAX);
    }

    private static EssaisRates lire(ResultSet r, int ligne) throws SQLException {
        Timestamp fin = r.getTimestamp(2);
        return new EssaisRates(r.getInt(1), fin == null ? null : fin.toInstant());
    }

    /** Essais ratés cumulés et fin du verrouillage (null si la connexion n'est pas verrouillée). */
    public record EssaisRates(int essais, Instant verrouilleJusqua) {

        public boolean verrouille(Instant maintenant) {
            return verrouilleJusqua != null && maintenant.isBefore(verrouilleJusqua);
        }
    }

    /** Supprime les fenêtres terminées et les identifiants inconnus oubliés ; renvoie le nombre de lignes. */
    public int purger(Instant fenetresAvant, Instant inconnusAvant) {
        return jdbc.update("delete from " + limites + " where fenetre < :f", Map.of("f", Timestamp.from(fenetresAvant)))
                + jdbc.update(
                        "delete from " + inconnus + " where mis_a_jour < :i",
                        Map.of("i", Timestamp.from(inconnusAvant)));
    }
}
