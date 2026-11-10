package ma.invento.pos.caisse.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class SessionCaisseTest {

    private static final Instant T0 = Instant.parse("2026-10-30T08:30:00Z");
    private static final ComptageTiroir VIDE =
            new ComptageTiroir(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    private static ComptageTiroir especes(String montant) {
        return new ComptageTiroir(new BigDecimal(montant), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void ecart_entre_le_compte_et_l_attendu_du_tiroir() {
        SessionCaisse session = new SessionCaisse(1, 2, new BigDecimal("500.00"), T0);
        assertThat(session.estOuverte()).isTrue();

        // Attendu = 500 + 260,60 espèces + 100 carte + 200 reçus − 300 versés = 760,60.
        session.cloturer(
                new BigDecimal("657.00"),
                new BigDecimal("98.00"),
                new ComptageTiroir(
                        new BigDecimal("260.60"),
                        new BigDecimal("100.00"),
                        new BigDecimal("200.00"),
                        new BigDecimal("300.00")),
                T0.plusSeconds(3600));

        assertThat(session.estOuverte()).isFalse();
        assertThat(session.getEcart()).isEqualByComparingTo("-5.60");
        assertThat(session.getMontantCompte()).isEqualByComparingTo("755.00");
        // Espèces attendues = 660,60, tickets TPE attendus = 100.
        assertThat(session.getEcartEspeces()).isEqualByComparingTo("-3.60");
        assertThat(session.getEcartTpe()).isEqualByComparingTo("-2.00");
        assertThat(session.getFermeture()).isEqualTo(T0.plusSeconds(3600));
        assertThat(session.isComptageEnAttente()).isFalse();
        assertThatThrownBy(() -> session.cloturer(BigDecimal.ZERO, BigDecimal.ZERO, VIDE, T0))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void cloture_d_office_attend_le_comptage_de_l_admin() {
        SessionCaisse session = new SessionCaisse(1, 2, new BigDecimal("500.00"), T0);

        session.cloturerDOffice(T0.plusSeconds(600));

        assertThat(session.estOuverte()).isFalse();
        assertThat(session.isClotureForcee()).isTrue();
        assertThat(session.isComptageEnAttente()).isTrue();
        assertThat(session.getFermeture()).isEqualTo(T0.plusSeconds(600));
        assertThat(session.getMontantCompte()).isNull();
        assertThat(session.getEcart()).isNull();
        assertThatThrownBy(() -> session.cloturerDOffice(T0)).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> session.cloturer(BigDecimal.ZERO, BigDecimal.ZERO, VIDE, T0))
                .isInstanceOf(RegleMetierException.class);

        session.terminerComptage(new BigDecimal("500.00"), new BigDecimal("20.00"), especes("10.00"));

        assertThat(session.isComptageEnAttente()).isFalse();
        assertThat(session.getMontantCompte()).isEqualByComparingTo("520.00");
        assertThat(session.getEcart()).isEqualByComparingTo("10.00");
        assertThat(session.getEcartEspeces()).isEqualByComparingTo("-10.00");
        assertThat(session.getEcartTpe()).isEqualByComparingTo("20.00");
        assertThatThrownBy(() -> session.terminerComptage(BigDecimal.ZERO, BigDecimal.ZERO, VIDE))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void cloture_normale_n_est_pas_forcee_et_ne_peut_plus_etre_forcee() {
        SessionCaisse session = new SessionCaisse(1, 2, BigDecimal.ZERO, T0);
        session.cloturer(BigDecimal.ZERO, BigDecimal.ZERO, VIDE, T0.plusSeconds(60));

        assertThat(session.isClotureForcee()).isFalse();
        assertThatThrownBy(() -> session.cloturerDOffice(T0)).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> session.terminerComptage(BigDecimal.ZERO, BigDecimal.ZERO, VIDE))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void refuse_les_montants_negatifs() {
        assertThatThrownBy(() -> new SessionCaisse(1, 2, new BigDecimal("-1"), T0))
                .isInstanceOf(RegleMetierException.class);
        SessionCaisse session = new SessionCaisse(1, 2, BigDecimal.ZERO, T0);
        assertThatThrownBy(() -> session.cloturer(new BigDecimal("-1"), BigDecimal.ZERO, VIDE, T0))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> session.cloturer(BigDecimal.ZERO, new BigDecimal("-1"), VIDE, T0))
                .isInstanceOf(RegleMetierException.class);
    }
}
