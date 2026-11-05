package ma.invento.pos.identite.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class SeuilsRemiseTest {

    @Test
    void seuils_entre_0_et_100() {
        assertThatThrownBy(() -> new SeuilsRemise(BigDecimal.valueOf(-1), BigDecimal.ZERO))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> new SeuilsRemise(BigDecimal.ZERO, new BigDecimal("100.01")))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> new SeuilsRemise(null, BigDecimal.ZERO)).isInstanceOf(RegleMetierException.class);
    }

    @Test
    void seuil_ligne_ou_ticket_selon_la_remise() {
        SeuilsRemise seuils = new SeuilsRemise(BigDecimal.TEN, BigDecimal.valueOf(5));
        assertThat(seuils.couvre(BigDecimal.TEN, false)).isTrue();
        assertThat(seuils.couvre(BigDecimal.TEN, true)).isFalse();
        assertThat(SeuilsRemise.AUCUN.aucun()).isTrue();
    }
}
