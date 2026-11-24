package ma.invento.pos.identite.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class IdentifiantConnexionTest {

    @Test
    void prenom_et_nom_saisis_donnent_la_forme_prenom_point_nom() {
        assertThat(IdentifiantConnexion.normaliser("  Mohamed   Alaoui ")).isEqualTo("mohamed.alaoui");
        assertThat(IdentifiantConnexion.normaliser("Hélène Aït-Ali")).isEqualTo("helene.ait-ali");
        assertThat(IdentifiantConnexion.normaliser("ÇA va")).isEqualTo("ca.va");
    }

    @Test
    void identifiant_deja_normalise_inchange_et_caracteres_inutilisables_retires() {
        assertThat(IdentifiantConnexion.normaliser("caissier")).isEqualTo("caissier");
        assertThat(IdentifiantConnexion.normaliser("Karim.B")).isEqualTo("karim.b");
        assertThat(IdentifiantConnexion.normaliser("o'neil . j")).isEqualTo("oneil.j");
        assertThat(IdentifiantConnexion.normaliser("كريم")).isEmpty();
        assertThat(IdentifiantConnexion.normaliser(null)).isEmpty();
    }

    @Test
    void forme_tronquee_a_la_longueur_de_la_colonne() {
        assertThat(IdentifiantConnexion.normaliser("a".repeat(80))).hasSize(IdentifiantConnexion.LONGUEUR_MAX);
    }

    @Test
    void proposition_suivie_d_un_chiffre_quand_elle_est_prise() {
        Set<String> pris = Set.of("mohamed.alaoui", "mohamed.alaoui2");
        assertThat(IdentifiantConnexion.proposer("Mohamed", "Alaoui", pris::contains))
                .isEqualTo("mohamed.alaoui3");
        assertThat(IdentifiantConnexion.proposer("Karim", "Bennani", pris::contains))
                .isEqualTo("karim.bennani");
        assertThat(IdentifiantConnexion.proposer("", "Alaoui", pris::contains)).isEqualTo("alaoui");
        assertThat(IdentifiantConnexion.proposer("", "", pris::contains)).isEmpty();
    }
}
