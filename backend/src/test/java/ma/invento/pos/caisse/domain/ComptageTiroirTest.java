package ma.invento.pos.caisse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ComptageTiroirTest {

    @Test
    void attendu_fond_plus_ventes_plus_recu_moins_verse() {
        ComptageTiroir tiroir = new ComptageTiroir(
                new BigDecimal("600"), new BigDecimal("400"), new BigDecimal("200"), new BigDecimal("200"));
        assertThat(tiroir.ventes()).isEqualByComparingTo("1000");
        assertThat(tiroir.attendu(new BigDecimal("1000"))).isEqualByComparingTo("2000");
    }

    @Test
    void un_manque_ne_reduit_pas_le_credit_un_excedent_s_y_ajoute() {
        assertThat(ComptageTiroir.creditCloture(new BigDecimal("1000"), new BigDecimal("-50")))
                .isEqualByComparingTo("1000");
        assertThat(ComptageTiroir.creditCloture(new BigDecimal("1000"), new BigDecimal("20")))
                .isEqualByComparingTo("1020");
    }
}
