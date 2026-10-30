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
    void attente_prolongee_puis_expiree_puis_annulee() {
        Vente vente = venteDe("10.00");
        vente.mettreEnAttente("Parti au DAB", T0.plusSeconds(300));
        assertThat(vente.estEnAttente()).isTrue();
        assertThat(vente.getNoteAttente()).isEqualTo("Parti au DAB");
        assertThat(vente.estExpiree(T0.plusSeconds(299))).isFalse();

        vente.prolongerAttente(T0.plusSeconds(600));
        assertThat(vente.estExpiree(T0.plusSeconds(300))).isFalse();
        assertThat(vente.estExpiree(T0.plusSeconds(600))).isTrue();

        vente.annulerAttente(T0.plusSeconds(601));
        assertThat(vente.getStatut()).isEqualTo(StatutVente.ANNULEE);
        assertThat(vente.getAttenteExpireLe()).isNull();
        assertThatThrownBy(() -> vente.annulerAttente(T0)).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> vente.prolongerAttente(T0)).isInstanceOf(RegleMetierException.class);
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
