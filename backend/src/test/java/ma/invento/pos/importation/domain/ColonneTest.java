package ma.invento.pos.importation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ColonneTest {

    @ParameterizedTest
    @CsvSource({
        "PID, pid",
        "Catégorie, categorie",
        "' Date de péremption ', date_de_peremption",
        "Code barre 1, code_barre_1",
        "code_barre_2, code_barre_2",
        "Stock min, stock_min",
        "quantite_reelle, quantite_reelle"
    })
    void normalise_les_entetes_sobrus_et_invento(String entete, String cle) {
        assertThat(Colonne.normaliser(entete)).isEqualTo(cle);
    }
}
