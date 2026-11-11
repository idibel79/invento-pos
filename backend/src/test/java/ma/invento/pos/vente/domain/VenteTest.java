package ma.invento.pos.vente.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class VenteTest {

    private static final Instant T0 = Instant.parse("2026-10-30T10:00:00Z");

    @Test
    void validation_en_especes_calcule_le_rendu() {
        Vente vente = venteDe("26.20");
        vente.validerEnEspeces(new BigDecimal("50.00"), "cle-validation", T0);

        assertThat(vente.getStatut()).isEqualTo(StatutVente.VALIDEE);
        assertThat(vente.getCleIdempotence()).isEqualTo("cle-validation");
        assertThat(vente.getFinaliseeLe()).isEqualTo(T0);
        assertThat(vente.getPaiements()).singleElement().satisfies(p -> {
            assertThat(p.getMode()).isEqualTo(ModePaiement.ESPECES);
            assertThat(p.getMontant()).isEqualByComparingTo("26.20");
            assertThat(p.getRendu()).isEqualByComparingTo("23.80");
        });
        assertThatThrownBy(() -> vente.mettreEnAttente(null, T0)).isInstanceOf(RegleMetierException.class);
    }

    @Test
    void validation_par_carte_debite_le_total_sans_rendu() {
        Vente vente = venteDe("26.20");
        vente.validerParCarte("cle-carte", T0);

        assertThat(vente.getStatut()).isEqualTo(StatutVente.VALIDEE);
        assertThat(vente.getCleIdempotence()).isEqualTo("cle-carte");
        assertThat(vente.getPaiements()).singleElement().satisfies(p -> {
            assertThat(p.getMode()).isEqualTo(ModePaiement.CARTE);
            assertThat(p.getMontant()).isEqualByComparingTo("26.20");
            assertThat(p.getMontantRecu()).isNull();
            assertThat(p.getRendu()).isNull();
        });
    }

    @Test
    void paiement_mixte_especes_puis_carte_pour_le_reste() {
        Vente vente = venteDe("26.20");
        vente.validerEspecesEtCarte(new BigDecimal("10.00"), "cle-mixte", T0);

        assertThat(vente.getStatut()).isEqualTo(StatutVente.VALIDEE);
        assertThat(vente.getCleIdempotence()).isEqualTo("cle-mixte");
        assertThat(vente.getPaiements()).hasSize(2);
        assertThat(vente.getPaiements().get(0).getMode()).isEqualTo(ModePaiement.ESPECES);
        assertThat(vente.getPaiements().get(0).getMontant()).isEqualByComparingTo("10.00");
        assertThat(vente.getPaiements().get(0).getRendu()).isEqualByComparingTo("0");
        assertThat(vente.getPaiements().get(1).getMode()).isEqualTo(ModePaiement.CARTE);
        assertThat(vente.getPaiements().get(1).getMontant()).isEqualByComparingTo("16.20");
    }

    @Test
    void paiement_mixte_refuse_une_part_especes_nulle_ou_qui_couvre_le_total() {
        Vente vente = venteDe("26.20");
        assertThatThrownBy(() -> vente.validerEspecesEtCarte(BigDecimal.ZERO, "cle", T0))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> vente.validerEspecesEtCarte(new BigDecimal("26.20"), "cle", T0))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void refuse_un_montant_recu_insuffisant() {
        Vente vente = venteDe("26.20");
        assertThatThrownBy(() -> vente.validerEnEspeces(new BigDecimal("26.19"), "cle", T0))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void vente_offerte_sans_paiement() {
        Vente vente = new Vente("C1-000002", "cle", 1, 2, 3, T0);
        vente.remplirPanier(
                CalculateurVente.calculer(
                        List.of(new LignePanier(
                                1,
                                1,
                                BigDecimal.TEN,
                                BigDecimal.ZERO,
                                new Remise(TypeRemise.POURCENT, BigDecimal.valueOf(100)))),
                        null),
                null,
                "Geste commercial",
                9L);
        vente.validerEnEspeces(BigDecimal.ZERO, "cle", T0);
        assertThat(vente.getPaiements()).isEmpty();
        assertThat(vente.getValideePar()).isEqualTo(9L);
        assertThat(vente.getLignes().getFirst().getRemise().type()).isEqualTo(TypeRemise.POURCENT);
    }

    @Test
    void attente_reprise_ne_peut_plus_expirer_puis_est_abandonnee() {
        Vente vente = venteDe("10.00");
        vente.mettreEnAttente(null, T0.plusSeconds(300));

        vente.reprendre();
        assertThat(vente.getStatut()).isEqualTo(StatutVente.REPRISE);
        assertThat(vente.estEnAttente()).isFalse();
        assertThat(vente.getAttenteExpireLe()).isNull();
        assertThat(vente.estExpiree(T0.plusSeconds(600))).isFalse();
        assertThatThrownBy(vente::reprendre).isInstanceOf(RegleMetierException.class);

        vente.annulerAttente(T0.plusSeconds(60));
        assertThat(vente.getStatut()).isEqualTo(StatutVente.ANNULEE);
    }

    @Test
    void attente_expiree_puis_annulee() {
        Vente vente = venteDe("10.00");
        vente.mettreEnAttente("Parti au DAB", T0.plusSeconds(300));
        assertThat(vente.estEnAttente()).isTrue();
        assertThat(vente.getNoteAttente()).isEqualTo("Parti au DAB");
        assertThat(vente.estExpiree(T0.plusSeconds(299))).isFalse();

        assertThat(vente.estExpiree(T0.plusSeconds(300))).isTrue();

        vente.annulerAttente(T0.plusSeconds(301));
        assertThat(vente.getStatut()).isEqualTo(StatutVente.ANNULEE);
        assertThat(vente.getAttenteExpireLe()).isNull();
        assertThatThrownBy(() -> vente.annulerAttente(T0)).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(vente::reprendre).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> vente.validerEnEspeces(BigDecimal.TEN, "x", T0))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void la_remise_ticket_est_conservee() {
        Vente vente = new Vente("C1-000003", "cle", 1, 2, 3, T0);
        Remise ticket = new Remise(TypeRemise.MONTANT, BigDecimal.ONE);
        vente.remplirPanier(
                CalculateurVente.calculer(
                        List.of(new LignePanier(1, 2, BigDecimal.TEN, BigDecimal.ZERO, null)), ticket),
                ticket,
                null,
                null);
        assertThat(vente.getRemiseTicket()).isEqualTo(ticket);
        assertThat(vente.getTotalTtc()).isEqualByComparingTo("19.00");
        assertThat(vente.getTotalHt()).isEqualByComparingTo("19.00");
        assertThat(vente.getTotalTva()).isEqualByComparingTo("0");
    }

    private static Vente venteDe(String prix) {
        Vente vente = new Vente("C1-000001", "cle-attente", 1, 2, 3, T0);
        vente.remplirPanier(
                CalculateurVente.calculer(
                        List.of(new LignePanier(1, 1, new BigDecimal(prix), BigDecimal.ZERO, null)), null),
                null,
                null,
                null);
        return vente;
    }
}
