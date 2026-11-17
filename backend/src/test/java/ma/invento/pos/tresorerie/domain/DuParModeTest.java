package ma.invento.pos.tresorerie.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class DuParModeTest {

    private static BigDecimal dh(String montant) {
        return new BigDecimal(montant);
    }

    @Test
    void le_reste_du_solde_est_du_en_especes() {
        DuParMode du = DuParMode.depuis(dh("500.00"), dh("120.00"));
        assertThat(du.especes()).isEqualByComparingTo("380.00");
        assertThat(du.tpe()).isEqualByComparingTo("120.00");
        assertThat(du.total()).isEqualByComparingTo("500.00");
    }

    @Test
    void le_du_tpe_ne_depasse_pas_le_solde_et_reste_positif() {
        assertThat(DuParMode.depuis(dh("100.00"), dh("150.00")).tpe()).isEqualByComparingTo("100.00");
        assertThat(DuParMode.depuis(dh("100.00"), dh("150.00")).especes()).isEqualByComparingTo("0");
        assertThat(DuParMode.depuis(dh("100.00"), dh("-20.00")).tpe()).isEqualByComparingTo("0");
        assertThat(DuParMode.depuis(dh("-30.00"), dh("50.00")).tpe()).isEqualByComparingTo("0");
    }

    @Test
    void un_versement_ne_depasse_le_du_dans_aucun_mode() {
        DuParMode du = DuParMode.depuis(dh("500.00"), dh("120.00"));
        assertThatCode(() -> du.verifierVersement(dh("380.00"), dh("120.00"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> du.verifierVersement(dh("380.01"), dh("0")))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("espèces");
        assertThatThrownBy(() -> du.verifierVersement(dh("0"), dh("120.01")))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("TPE");
    }
}
