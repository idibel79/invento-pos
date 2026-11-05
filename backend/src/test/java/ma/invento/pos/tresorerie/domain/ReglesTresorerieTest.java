package ma.invento.pos.tresorerie.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import ma.invento.pos.shared.domain.ConflitException;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class ReglesTresorerieTest {

    @Test
    void montant_strictement_positif_au_centime() {
        assertThatCode(() -> ReglesTresorerie.verifierMontant(new BigDecimal("0.01")))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReglesTresorerie.verifierMontant(new BigDecimal("10.500")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ReglesTresorerie.verifierMontant(BigDecimal.ZERO))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> ReglesTresorerie.verifierMontant(null)).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> ReglesTresorerie.verifierMontant(new BigDecimal("1.005")))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void motif_nettoye_obligatoire_ou_facultatif() {
        assertThat(ReglesTresorerie.motif("  dépôt banque ", true)).isEqualTo("dépôt banque");
        assertThat(ReglesTresorerie.motif("  ", false)).isNull();
        assertThat(ReglesTresorerie.motif(null, false)).isNull();
        assertThatThrownBy(() -> ReglesTresorerie.motif(" ", true)).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> ReglesTresorerie.motif("x".repeat(301), false))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void versement_entre_un_caissier_et_la_caisse_principale_seulement() {
        assertThatCode(() -> ReglesTresorerie.verifierSens(TypeCompte.CAISSIER, TypeCompte.PRINCIPALE))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReglesTresorerie.verifierSens(TypeCompte.PRINCIPALE, TypeCompte.CAISSIER))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ReglesTresorerie.verifierSens(TypeCompte.CAISSIER, TypeCompte.CAISSIER))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> ReglesTresorerie.verifierSens(TypeCompte.PRINCIPALE, TypeCompte.PRINCIPALE))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void seul_un_versement_en_transit_peut_etre_traite() {
        assertThatCode(StatutVersement.EN_TRANSIT::verifierEnTransit).doesNotThrowAnyException();
        for (StatutVersement statut :
                new StatutVersement[] {StatutVersement.VALIDE, StatutVersement.REFUSE, StatutVersement.ANNULE}) {
            assertThatThrownBy(statut::verifierEnTransit)
                    .isInstanceOf(ConflitException.class)
                    .hasMessageContaining(statut.libelle());
        }
        assertThat(StatutVersement.EN_TRANSIT.libelle()).isEqualTo("en transit");
    }
}
