package ma.invento.pos.client.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;

class FicheClientTest {

    private static FicheClient entreprise(String ice, String email) {
        return ReglesClient.fiche(
                TypeClient.ENTREPRISE,
                " Épicerie  Atlas SARL ",
                "05 22 40 18 90",
                null,
                null,
                ice,
                " 1234567 ",
                "RC 45678",
                "Rachid Amrani",
                "12, rue Ibn Sina",
                email);
    }

    @Test
    void type_absent_vaut_particulier() {
        FicheClient fiche =
                ReglesClient.fiche(null, "Hassan Alami", null, null, null, null, null, null, null, null, null);

        assertThat(fiche.type()).isEqualTo(TypeClient.PARTICULIER);
    }

    @Test
    void les_champs_entreprise_sont_ignores_pour_un_particulier() {
        FicheClient fiche = ReglesClient.fiche(
                TypeClient.PARTICULIER,
                "Hassan Alami",
                null,
                null,
                null,
                "pas un ice",
                "1",
                "RC 1",
                "Contact",
                "Adresse",
                "pas un email");

        assertThat(fiche.ice()).isNull();
        assertThat(fiche.identifiantFiscal()).isNull();
        assertThat(fiche.rc()).isNull();
        assertThat(fiche.contact()).isNull();
        assertThat(fiche.adresse()).isNull();
        assertThat(fiche.email()).isNull();
    }

    @Test
    void fiche_entreprise_nettoyee() {
        FicheClient fiche = entreprise("001 234 567 000 089", " contact@atlas.ma ");

        assertThat(fiche.nom()).isEqualTo("Épicerie Atlas SARL");
        assertThat(fiche.ice()).isEqualTo("001234567000089");
        assertThat(fiche.identifiantFiscal()).isEqualTo("1234567");
        assertThat(fiche.email()).isEqualTo("contact@atlas.ma");
    }

    @Test
    void ice_et_email_sont_facultatifs() {
        FicheClient fiche = entreprise("  ", "");

        assertThat(fiche.ice()).isNull();
        assertThat(fiche.email()).isNull();
    }

    @Test
    void ice_de_15_chiffres_seulement() {
        assertThatThrownBy(() -> entreprise("12345", null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("15 chiffres");
        assertThatThrownBy(() -> entreprise("00123456700008A", null)).isInstanceOf(RegleMetierException.class);
    }

    @Test
    void email_invalide_refuse() {
        assertThatThrownBy(() -> entreprise(null, "contact@atlas"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("email");
    }

    @Test
    void raison_sociale_obligatoire() {
        assertThatThrownBy(() -> ReglesClient.fiche(
                        TypeClient.ENTREPRISE, " ", null, null, null, null, null, null, null, null, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("raison sociale");
    }
}
