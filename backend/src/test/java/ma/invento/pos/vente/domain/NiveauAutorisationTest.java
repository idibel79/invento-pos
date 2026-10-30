package ma.invento.pos.vente.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.parametre.domain.PlafondsRemise;
import org.junit.jupiter.api.Test;

class NiveauAutorisationTest {

    private static final PlafondsRemise PLAFONDS = new PlafondsRemise(BigDecimal.TEN, BigDecimal.valueOf(30));

    @Test
    void niveau_selon_les_plafonds_bornes_incluses() {
        assertThat(NiveauAutorisation.requis(BigDecimal.ZERO, PLAFONDS)).isEqualTo(NiveauAutorisation.AUCUNE);
        assertThat(NiveauAutorisation.requis(BigDecimal.TEN, PLAFONDS)).isEqualTo(NiveauAutorisation.AUCUNE);
        assertThat(NiveauAutorisation.requis(new BigDecimal("10.01"), PLAFONDS))
                .isEqualTo(NiveauAutorisation.RESPONSABLE);
        assertThat(NiveauAutorisation.requis(BigDecimal.valueOf(30), PLAFONDS))
                .isEqualTo(NiveauAutorisation.RESPONSABLE);
        assertThat(NiveauAutorisation.requis(new BigDecimal("30.01"), PLAFONDS)).isEqualTo(NiveauAutorisation.ADMIN);
    }

    @Test
    void roles_qui_couvrent_chaque_niveau() {
        assertThat(NiveauAutorisation.AUCUNE.estCouvertPar(Role.CAISSIER)).isTrue();
        assertThat(NiveauAutorisation.RESPONSABLE.estCouvertPar(Role.CAISSIER)).isFalse();
        assertThat(NiveauAutorisation.RESPONSABLE.estCouvertPar(Role.RESPONSABLE))
                .isTrue();
        assertThat(NiveauAutorisation.RESPONSABLE.estCouvertPar(Role.ADMIN)).isTrue();
        assertThat(NiveauAutorisation.ADMIN.estCouvertPar(Role.RESPONSABLE)).isFalse();
        assertThat(NiveauAutorisation.ADMIN.estCouvertPar(Role.ADMIN)).isTrue();
    }
}
