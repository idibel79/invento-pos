package ma.invento.pos.client.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class ReglesClientTest {

    @Test
    void nom_est_nettoye() {
        assertThat(ReglesClient.nom("  Fatima   El  Amrani ")).isEqualTo("Fatima El Amrani");
    }

    @Test
    void nom_vide_ou_absent_est_refuse() {
        assertThatThrownBy(() -> ReglesClient.nom(null)).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> ReglesClient.nom("   "))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("obligatoire");
    }

    @Test
    void nom_trop_long_est_refuse() {
        assertThat(ReglesClient.nom("a".repeat(ReglesClient.NOM_MAX))).hasSize(ReglesClient.NOM_MAX);
        assertThatThrownBy(() -> ReglesClient.nom("a".repeat(ReglesClient.NOM_MAX + 1)))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining(String.valueOf(ReglesClient.NOM_MAX));
    }

    @Test
    void texte_facultatif_vide_devient_null() {
        assertThat(ReglesClient.facultatif(null, 30, "Le téléphone")).isNull();
        assertThat(ReglesClient.facultatif("   ", 30, "Le téléphone")).isNull();
        assertThat(ReglesClient.facultatif(" 0612345678 ", 30, "Le téléphone")).isEqualTo("0612345678");
    }

    @Test
    void texte_facultatif_trop_long_est_refuse() {
        assertThatThrownBy(() -> ReglesClient.facultatif("x".repeat(31), 30, "Le téléphone"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessage("Le téléphone ne doit pas dépasser 30 caractères.");
    }

    @Test
    void remboursement_additionne_especes_et_tpe() {
        assertThat(ReglesClient.remboursement(new BigDecimal("50.00"), new BigDecimal("25.50")))
                .isEqualByComparingTo("75.50");
        assertThat(ReglesClient.remboursement(new BigDecimal("40"), null)).isEqualByComparingTo("40");
        assertThat(ReglesClient.remboursement(null, new BigDecimal("12.3"))).isEqualByComparingTo("12.30");
    }

    @Test
    void remboursement_negatif_est_refuse() {
        assertThatThrownBy(() -> ReglesClient.remboursement(new BigDecimal("-1"), BigDecimal.TEN))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("négatifs");
        assertThatThrownBy(() -> ReglesClient.remboursement(BigDecimal.TEN, new BigDecimal("-1")))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void remboursement_au_dela_du_centime_est_refuse() {
        assertThatThrownBy(() -> ReglesClient.remboursement(new BigDecimal("1.005"), null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("centime");
        assertThatThrownBy(() -> ReglesClient.remboursement(null, new BigDecimal("0.001")))
                .isInstanceOf(RegleMetierException.class);
        assertThat(ReglesClient.remboursement(new BigDecimal("1.500"), null)).isEqualByComparingTo("1.50");
    }

    @Test
    void remboursement_nul_est_refuse() {
        assertThatThrownBy(() -> ReglesClient.remboursement(null, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("montant");
        assertThatThrownBy(() -> ReglesClient.remboursement(BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void plafond_absent_signifie_sans_limite() {
        assertThat(ReglesClient.plafond(null)).isNull();
        assertThat(ReglesClient.plafond(new BigDecimal("500.00"))).isEqualByComparingTo("500");
    }

    @Test
    void plafond_nul_negatif_ou_trop_precis_est_refuse() {
        assertThatThrownBy(() -> ReglesClient.plafond(BigDecimal.ZERO)).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> ReglesClient.plafond(new BigDecimal("-10"))).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> ReglesClient.plafond(new BigDecimal("10.001")))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("centime");
    }

    @Test
    void vente_a_credit_exige_un_total_positif() {
        assertThatCode(() -> ReglesClient.verifierVenteACredit(new BigDecimal("0.01")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ReglesClient.verifierVenteACredit(null)).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> ReglesClient.verifierVenteACredit(BigDecimal.ZERO))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("crédit");
    }

    @Test
    void types_de_mouvement() {
        assertThat(TypeMouvementClient.values())
                .containsExactly(
                        TypeMouvementClient.VENTE_CREDIT,
                        TypeMouvementClient.REMBOURSEMENT,
                        TypeMouvementClient.ANNULATION_REMBOURSEMENT);
    }
}
