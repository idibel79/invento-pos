package ma.invento.pos.identite.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UtilisateurTest {

    private Utilisateur caissier() {
        return new Utilisateur("Alaoui", "Sara", "sara", Role.CAISSIER, "hash", Instant.EPOCH);
    }

    @Test
    void nouveau_compte_doit_changer_son_mot_de_passe() {
        Utilisateur u = caissier();
        assertThat(u.isDoitChangerMotDePasse()).isTrue();
        assertThat(u.peutSeConnecter()).isTrue();
        u.changerMotDePasse("nouveau");
        assertThat(u.isDoitChangerMotDePasse()).isFalse();
        assertThat(u.getMotDePasseHash()).isEqualTo("nouveau");
    }

    @Test
    void reinitialisation_deverrouille_et_impose_un_changement() {
        Utilisateur u = caissier();
        u.changerMotDePasse("perso");
        u.reinitialiserMotDePasse("temporaire");
        assertThat(u.isDoitChangerMotDePasse()).isTrue();
        assertThat(u.isVerrouille()).isFalse();
    }

    @Test
    void desactive_ne_peut_pas_se_connecter_et_garde_ses_caisses() {
        Utilisateur u = caissier();
        u.modifierProfil("Alaoui", "Sara", Role.RESPONSABLE, false, Set.of(1L, 2L));
        assertThat(u.peutSeConnecter()).isFalse();
        assertThat(u.getRole()).isEqualTo(Role.RESPONSABLE);
        assertThat(u.getCaisseIds()).containsExactlyInAnyOrder(1L, 2L);
        u.reactiver();
        assertThat(u.peutSeConnecter()).isTrue();
    }

    @Test
    void pin_defini_et_connexion_reussie() {
        Utilisateur u = caissier();
        assertThat(u.isPinDefini()).isFalse();
        u.definirPin("pin-hash");
        assertThat(u.isPinDefini()).isTrue();
        u.connexionReussie(Instant.EPOCH);
        assertThat(u.getDerniereConnexion()).isEqualTo(Instant.EPOCH);
        assertThat(u.getEssaisRates()).isZero();
    }
}
