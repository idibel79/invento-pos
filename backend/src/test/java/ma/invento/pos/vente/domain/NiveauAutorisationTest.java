package ma.invento.pos.vente.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.SeuilsRemise;
import org.junit.jupiter.api.Test;

class NiveauAutorisationTest {

    private static final SeuilsRemise SEUILS = new SeuilsRemise(BigDecimal.TEN, BigDecimal.valueOf(5));

    @Test
    void sans_remise_tout_le_monde_couvre() {
        PanierCalcule panier = panier(null, null);
        assertThat(NiveauAutorisation.couvre(Role.CAISSIER, SeuilsRemise.AUCUN, panier))
                .isTrue();
    }

    @Test
    void un_caissier_n_accorde_aucune_remise_et_l_admin_toutes() {
        PanierCalcule panier = panier(new Remise(TypeRemise.POURCENT, BigDecimal.ONE), null);
        assertThat(NiveauAutorisation.couvre(Role.CAISSIER, SEUILS, panier)).isFalse();
        assertThat(NiveauAutorisation.couvre(Role.ADMIN, SeuilsRemise.AUCUN, panier))
                .isTrue();
    }

    @Test
    void un_responsable_dans_ses_seuils_par_ligne_ou_sur_le_ticket_bornes_incluses() {
        assertThat(couvreResponsable(panier(pourcent("10"), null))).isTrue();
        assertThat(couvreResponsable(panier(pourcent("10.01"), null))).isFalse();
        assertThat(couvreResponsable(panier(null, pourcent("5")))).isTrue();
        assertThat(couvreResponsable(panier(null, pourcent("6")))).isFalse();
        assertThat(NiveauAutorisation.couvre(Role.RESPONSABLE, SeuilsRemise.AUCUN, panier(pourcent("1"), null)))
                .isFalse();
    }

    @Test
    void niveau_requis() {
        assertThat(NiveauAutorisation.requis(true, false)).isEqualTo(NiveauAutorisation.AUCUNE);
        assertThat(NiveauAutorisation.requis(false, true)).isEqualTo(NiveauAutorisation.RESPONSABLE);
        assertThat(NiveauAutorisation.requis(false, false)).isEqualTo(NiveauAutorisation.ADMIN);
    }

    private static boolean couvreResponsable(PanierCalcule panier) {
        return NiveauAutorisation.couvre(Role.RESPONSABLE, SEUILS, panier);
    }

    private static Remise pourcent(String valeur) {
        return new Remise(TypeRemise.POURCENT, new BigDecimal(valeur));
    }

    private static PanierCalcule panier(Remise remiseLigne, Remise remiseTicket) {
        LignePanier ligne = new LignePanier(1, 1, new BigDecimal("100.00"), BigDecimal.valueOf(20), remiseLigne);
        return CalculateurVente.calculer(List.of(ligne), remiseTicket);
    }
}
