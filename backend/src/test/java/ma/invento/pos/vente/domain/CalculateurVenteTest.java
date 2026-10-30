package ma.invento.pos.vente.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.vente.domain.PanierCalcule.VentilationTva;
import org.junit.jupiter.api.Test;

class CalculateurVenteTest {

    private static final BigDecimal TVA_20 = new BigDecimal("20.00");
    private static final BigDecimal EXONERE = new BigDecimal("0.00");

    @Test
    void sans_remise_le_total_est_la_somme_des_prix_ttc_et_la_tva_est_ventilee_par_taux() {
        PanierCalcule panier = CalculateurVente.calculer(
                List.of(ligne(1, 2, "13.70", EXONERE, null), ligne(2, 1, "125.00", TVA_20, null)), null);

        assertThat(panier.sousTotal()).isEqualByComparingTo("152.40");
        assertThat(panier.totalTtc()).isEqualByComparingTo("152.40");
        assertThat(panier.totalRemises()).isEqualByComparingTo("0");
        assertThat(panier.tva())
                .extracting(VentilationTva::taux)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("20.00"));
        VentilationTva vingt = panier.tva().get(1);
        assertThat(vingt.totalHt()).isEqualByComparingTo("104.17");
        assertThat(vingt.totalTva()).isEqualByComparingTo("20.83");
        assertThat(panier.totalHt()).isEqualByComparingTo("131.57");
        assertThat(panier.totalTva()).isEqualByComparingTo("20.83");
        assertThat(panier.totalHt().add(panier.totalTva())).isEqualByComparingTo(panier.totalTtc());
        assertThat(panier.tauxRemiseMax()).isEqualByComparingTo("0");
    }

    @Test
    void remise_ligne_en_pourcentage_et_en_montant() {
        PanierCalcule panier = CalculateurVente.calculer(
                List.of(
                        ligne(1, 1, "125.00", TVA_20, new Remise(TypeRemise.POURCENT, new BigDecimal("15"))),
                        ligne(2, 2, "10.00", TVA_20, new Remise(TypeRemise.MONTANT, new BigDecimal("1.00")))),
                null);

        assertThat(panier.lignes().get(0).remiseMontant()).isEqualByComparingTo("18.75");
        assertThat(panier.lignes().get(0).montantNet()).isEqualByComparingTo("106.25");
        assertThat(panier.lignes().get(1).remiseMontant()).isEqualByComparingTo("1.00");
        assertThat(panier.remisesLignes()).isEqualByComparingTo("19.75");
        assertThat(panier.totalTtc()).isEqualByComparingTo("125.25");
        assertThat(panier.tauxRemiseMax()).isEqualByComparingTo("15.00");
    }

    @Test
    void remise_ticket_appliquee_apres_les_remises_lignes_et_repartie_sur_les_taux() {
        PanierCalcule panier = CalculateurVente.calculer(
                List.of(ligne(1, 1, "100.00", TVA_20, null), ligne(2, 1, "50.00", EXONERE, null)),
                new Remise(TypeRemise.MONTANT, new BigDecimal("30.00")));

        assertThat(panier.remiseTicket()).isEqualByComparingTo("30.00");
        assertThat(panier.totalTtc()).isEqualByComparingTo("120.00");
        // 30 DH répartis au prorata : 20 DH sur la ligne à 20 %, 10 DH sur la ligne exonérée.
        assertThat(panier.tva()).hasSize(2);
        assertThat(panier.tva().get(0).totalTtc()).isEqualByComparingTo("40.00");
        assertThat(panier.tva().get(1).totalTtc()).isEqualByComparingTo("80.00");
        assertThat(panier.tva().get(1).totalHt()).isEqualByComparingTo("66.67");
        assertThat(panier.tauxRemiseMax()).isEqualByComparingTo("20.00");
    }

    @Test
    void la_repartition_de_la_remise_ticket_ne_perd_aucun_centime() {
        PanierCalcule panier = CalculateurVente.calculer(
                List.of(
                        ligne(1, 1, "0.10", TVA_20, null),
                        ligne(2, 1, "0.10", EXONERE, null),
                        ligne(3, 1, "0.10", TVA_20, null)),
                new Remise(TypeRemise.MONTANT, new BigDecimal("0.10")));

        BigDecimal ttc = panier.tva().stream().map(VentilationTva::totalTtc).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(ttc).isEqualByComparingTo(panier.totalTtc()).isEqualByComparingTo("0.20");
    }

    @Test
    void cumuler_remise_ligne_et_remise_ticket_compte_comme_une_seule_remise() {
        PanierCalcule panier = CalculateurVente.calculer(
                List.of(ligne(1, 1, "100.00", TVA_20, new Remise(TypeRemise.POURCENT, new BigDecimal("10")))),
                new Remise(TypeRemise.POURCENT, new BigDecimal("10")));

        assertThat(panier.totalTtc()).isEqualByComparingTo("81.00");
        assertThat(panier.tauxRemiseMax()).isEqualByComparingTo("19.00");
    }

    @Test
    void le_reste_d_arrondi_de_la_remise_ticket_ne_rend_aucun_taux_negatif() {
        // La dernière ligne est remisée à 100 % : le centime d'arrondi va à la ligne la plus forte.
        PanierCalcule panier = CalculateurVente.calculer(
                List.of(
                        ligne(1, 1, "0.10", TVA_20, null),
                        ligne(2, 1, "0.10", TVA_20, null),
                        ligne(3, 1, "0.10", TVA_20, null),
                        ligne(4, 1, "5.00", EXONERE, new Remise(TypeRemise.POURCENT, new BigDecimal("100")))),
                new Remise(TypeRemise.MONTANT, new BigDecimal("0.01")));

        assertThat(panier.tva())
                .allSatisfy(t -> assertThat(t.totalTtc().signum()).isNotNegative());
        assertThat(panier.tva().stream().map(VentilationTva::totalTtc).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(panier.totalTtc())
                .isEqualByComparingTo("0.29");
    }

    @Test
    void remise_totale_possible_et_panier_a_zero() {
        PanierCalcule panier = CalculateurVente.calculer(
                List.of(ligne(1, 1, "10.00", TVA_20, new Remise(TypeRemise.POURCENT, new BigDecimal("100")))),
                new Remise(TypeRemise.POURCENT, new BigDecimal("50")));

        assertThat(panier.totalTtc()).isEqualByComparingTo("0");
        assertThat(panier.remiseTicket()).isEqualByComparingTo("0");
        assertThat(panier.tauxRemiseMax()).isEqualByComparingTo("100");
    }

    @Test
    void une_remise_en_montant_ne_depasse_pas_sa_base() {
        assertThatThrownBy(() -> CalculateurVente.calculer(
                        List.of(ligne(1, 1, "10.00", TVA_20, new Remise(TypeRemise.MONTANT, new BigDecimal("10.01")))),
                        null))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> CalculateurVente.calculer(
                        List.of(ligne(1, 1, "10.00", TVA_20, null)),
                        new Remise(TypeRemise.MONTANT, new BigDecimal("11"))))
                .isInstanceOf(RegleMetierException.class);
    }

    private static LignePanier ligne(long id, int quantite, String prix, BigDecimal tva, Remise remise) {
        return new LignePanier(id, quantite, new BigDecimal(prix), tva, remise);
    }
}
