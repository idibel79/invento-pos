package ma.invento.pos.caisse.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class SessionCaisseTest {

    private static final Instant T0 = Instant.parse("2026-10-30T08:30:00Z");

    @Test
    void ecart_entre_le_compte_et_le_fond_plus_les_especes() {
        SessionCaisse session = new SessionCaisse(1, 2, new BigDecimal("500.00"), T0);
        assertThat(session.estOuverte()).isTrue();

        session.cloturer(new BigDecimal("755.00"), new BigDecimal("260.60"), T0.plusSeconds(3600));

        assertThat(session.estOuverte()).isFalse();
        assertThat(session.getEcart()).isEqualByComparingTo("-5.60");
        assertThat(session.getMontantCompte()).isEqualByComparingTo("755.00");
        assertThat(session.getFermeture()).isEqualTo(T0.plusSeconds(3600));
        assertThatThrownBy(() -> session.cloturer(BigDecimal.ZERO, BigDecimal.ZERO, T0))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void refuse_les_montants_negatifs() {
        assertThatThrownBy(() -> new SessionCaisse(1, 2, new BigDecimal("-1"), T0))
                .isInstanceOf(RegleMetierException.class);
        SessionCaisse session = new SessionCaisse(1, 2, BigDecimal.ZERO, T0);
        assertThatThrownBy(() -> session.cloturer(new BigDecimal("-1"), BigDecimal.ZERO, T0))
                .isInstanceOf(RegleMetierException.class);
    }
}
