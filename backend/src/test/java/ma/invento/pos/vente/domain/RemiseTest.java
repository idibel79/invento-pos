package ma.invento.pos.vente.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class RemiseTest {

    @Test
    void pourcentage_arrondi_au_centime() {
        assertThat(new Remise(TypeRemise.POURCENT, new BigDecimal("12.5")).montantSur(new BigDecimal("13.70")))
                .isEqualByComparingTo("1.71");
    }

    @Test
    void remise_nulle_ou_absente_equivaut_a_aucune_remise() {
        assertThat(Remise.ou(TypeRemise.POURCENT, BigDecimal.ZERO)).isNull();
        assertThat(Remise.ou(null, BigDecimal.ONE)).isNull();
        assertThat(Remise.ou(TypeRemise.MONTANT, null)).isNull();
        assertThat(Remise.ou(TypeRemise.MONTANT, BigDecimal.ONE)).isNotNull();
    }

    @Test
    void refuse_les_valeurs_invalides() {
        assertThatThrownBy(() -> new Remise(TypeRemise.POURCENT, new BigDecimal("100.01")))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> new Remise(TypeRemise.MONTANT, new BigDecimal("-1")))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> new Remise(null, BigDecimal.ONE)).isInstanceOf(RegleMetierException.class);
    }
}
