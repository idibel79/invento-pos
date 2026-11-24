package ma.invento.pos.tresorerie.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class PlafondsVersementTest {

    private static BigDecimal dh(String montant) {
        return new BigDecimal(montant);
    }

    @Test
    void caisse_ouverte_le_du_se_partage_entre_le_tiroir_et_ce_qui_est_hors_tiroir() {
        // Dû 1000 en espèces, fond 500 dans le tiroir, aucune vente : 500 de chaque côté.
        PlafondsVersement plafonds =
                PlafondsVersement.enSession(DuParMode.depuis(dh("1000"), BigDecimal.ZERO), dh("500"), BigDecimal.ZERO);

        assertThat(plafonds.tiroirEspeces()).isEqualByComparingTo("500");
        assertThat(plafonds.compteEspeces()).isEqualByComparingTo("500");
        plafonds.verifier(dh("500"), BigDecimal.ZERO, dh("500"), BigDecimal.ZERO);
    }

    @Test
    void les_ventes_de_la_session_peuvent_etre_versees_depuis_le_tiroir() {
        // Dû 0 avant la session, fond 0, 300 de ventes espèces et 200 par carte dans le tiroir.
        PlafondsVersement plafonds =
                PlafondsVersement.enSession(DuParMode.depuis(dh("500"), dh("200")), dh("300"), dh("200"));

        assertThat(plafonds.tiroirEspeces()).isEqualByComparingTo("300");
        assertThat(plafonds.tiroirTpe()).isEqualByComparingTo("200");
        assertThat(plafonds.compteEspeces()).isEqualByComparingTo("0");
        assertThat(plafonds.compteTpe()).isEqualByComparingTo("0");
    }

    @Test
    void une_part_au_dessus_de_son_plafond_est_refusee() {
        PlafondsVersement plafonds =
                PlafondsVersement.enSession(DuParMode.depuis(dh("1000"), BigDecimal.ZERO), dh("500"), BigDecimal.ZERO);

        assertThatThrownBy(() -> plafonds.verifier(BigDecimal.ZERO, BigDecimal.ZERO, dh("1000"), BigDecimal.ZERO))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("500");
    }

    @Test
    void sans_caisse_ouverte_tout_est_hors_tiroir() {
        PlafondsVersement plafonds = PlafondsVersement.horsSession(DuParMode.depuis(dh("800"), dh("300")));

        assertThat(plafonds.tiroirEspeces()).isZero();
        assertThat(plafonds.compteEspeces()).isEqualByComparingTo("500");
        assertThat(plafonds.compteTpe()).isEqualByComparingTo("300");
    }
}
