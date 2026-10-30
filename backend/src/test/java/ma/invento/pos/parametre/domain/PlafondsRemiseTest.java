package ma.invento.pos.parametre.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class PlafondsRemiseTest {

    @Test
    void accepte_les_valeurs_par_defaut_et_les_bornes() {
        assertThatCode(() -> new PlafondsRemise(BigDecimal.TEN, BigDecimal.valueOf(30)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new PlafondsRemise(BigDecimal.ZERO, BigDecimal.valueOf(100)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new PlafondsRemise(BigDecimal.TEN, BigDecimal.TEN)).doesNotThrowAnyException();
    }

    @Test
    void plafond_caissier_ne_depasse_pas_le_plafond_responsable() {
        assertThatThrownBy(() -> new PlafondsRemise(BigDecimal.valueOf(40), BigDecimal.valueOf(30)))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("caissier");
    }

    @Test
    void refuse_hors_bornes_ou_absent() {
        assertThatThrownBy(() -> new PlafondsRemise(BigDecimal.valueOf(-1), BigDecimal.TEN))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> new PlafondsRemise(BigDecimal.TEN, BigDecimal.valueOf(101)))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> new PlafondsRemise(null, BigDecimal.TEN)).isInstanceOf(RegleMetierException.class);
    }
}
