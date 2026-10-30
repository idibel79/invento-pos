package ma.invento.pos.identite.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class PolitiqueSecretsTest {

    @Test
    void mot_de_passe_valide() {
        assertThatCode(() -> PolitiqueSecrets.verifierMotDePasse("karim", "caisse-2026"))
                .doesNotThrowAnyException();
    }

    @Test
    void mot_de_passe_trop_court_trop_long_ou_egal_au_login() {
        assertThatThrownBy(() -> PolitiqueSecrets.verifierMotDePasse("karim", "court"))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> PolitiqueSecrets.verifierMotDePasse("karim", "é".repeat(40)))
                .hasMessageContaining("trop long");
        assertThatThrownBy(() -> PolitiqueSecrets.verifierMotDePasse("caissier1", "CAISSIER1"))
                .hasMessageContaining("identifiant");
        assertThatThrownBy(() -> PolitiqueSecrets.verifierMotDePasse("karim", null))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void pin_a_quatre_chiffres() {
        assertThatCode(() -> PolitiqueSecrets.verifierPin("0420")).doesNotThrowAnyException();
        assertThatThrownBy(() -> PolitiqueSecrets.verifierPin("123")).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> PolitiqueSecrets.verifierPin("12a4")).isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> PolitiqueSecrets.verifierPin(null)).isInstanceOf(RegleMetierException.class);
    }

    @Test
    void code_de_secours_saisi_sans_casse_espaces_ni_tirets() {
        assertThat(PolitiqueSecrets.normaliserCodeSecours(" abcde-FGHJK klmnp-qrstu "))
                .isEqualTo("ABCDEFGHJKKLMNPQRSTU");
        assertThat(PolitiqueSecrets.normaliserCodeSecours(null)).isEmpty();
    }
}
