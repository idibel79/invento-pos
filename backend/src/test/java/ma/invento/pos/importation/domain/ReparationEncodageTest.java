package ma.invento.pos.importation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReparationEncodageTest {

    @ParameterizedTest
    @CsvSource({
        "CatÃ©gorie, Catégorie",
        "MÃ©dicament (33.930%), Médicament (33.930%)",
        "ExonÃ©rÃ© (0.00%), Exonéré (0.00%)",
        "DiÃ©tÃ©tique (15.000%), Diététique (15.000%)",
        "Date de pÃ©remption, Date de péremption"
    })
    void repare_le_double_encodage_des_exports_sobrus(String casse, String attendu) {
        assertThat(ReparationEncodage.reparer(casse)).isEqualTo(attendu);
    }

    @ParameterizedTest
    @CsvSource({"Catégorie", "Parapharmacie (33.330%)", "DOLIPRANE CO 500MG", "Comptoir ", "Été à l'ombre"})
    void laisse_intact_un_texte_deja_correct(String texte) {
        assertThat(ReparationEncodage.reparer(texte)).isEqualTo(texte);
    }

    @ParameterizedTest
    @CsvSource({"Prix en €", "‘guillemets’"})
    void laisse_intact_un_texte_avec_des_caracteres_hors_latin1(String texte) {
        assertThat(ReparationEncodage.reparer(texte)).isEqualTo(texte);
    }

    @Test
    void accepte_vide_et_null() {
        assertThat(ReparationEncodage.reparer("")).isEmpty();
        assertThat(ReparationEncodage.reparer(null)).isNull();
    }
}
