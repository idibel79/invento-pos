package ma.invento.pos.facture.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import ma.invento.pos.shared.domain.RegleMetierException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReglesFactureTest {

    @Test
    void numero_sur_cinq_chiffres_par_annee() {
        assertThat(ReglesFacture.numero(2026, 42)).isEqualTo("FA-2026-00042");
        assertThat(ReglesFacture.numero(2027, 1)).isEqualTo("FA-2027-00001");
        assertThatThrownBy(() -> ReglesFacture.numero(2026, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "0|zéro",
                "1|un",
                "16|seize",
                "17|dix-sept",
                "21|vingt et un",
                "22|vingt-deux",
                "70|soixante-dix",
                "71|soixante et onze",
                "77|soixante-dix-sept",
                "80|quatre-vingts",
                "81|quatre-vingt-un",
                "91|quatre-vingt-onze",
                "99|quatre-vingt-dix-neuf",
                "100|cent",
                "101|cent un",
                "200|deux cents",
                "260|deux cent soixante",
                "1000|mille",
                "1260|mille deux cent soixante",
                "2000|deux mille",
                "80000|quatre-vingt mille",
                "200000|deux cent mille",
                "1000000|un million",
                "2500000|deux millions cinq cent mille",
                "1000000000|un milliard"
            })
    void nombre_en_lettres(long nombre, String attendu) {
        assertThat(ReglesFacture.nombre(nombre)).isEqualTo(attendu);
    }

    @Test
    void montant_en_dirhams_et_centimes() {
        assertThat(ReglesFacture.montantEnLettres(new BigDecimal("1260.50")))
                .isEqualTo("mille deux cent soixante dirhams et cinquante centimes");
        assertThat(ReglesFacture.montantEnLettres(new BigDecimal("1.01"))).isEqualTo("un dirham et un centime");
        assertThat(ReglesFacture.montantEnLettres(new BigDecimal("80.00"))).isEqualTo("quatre-vingts dirhams");
        assertThat(ReglesFacture.montantEnLettres(new BigDecimal("0.75"))).isEqualTo("soixante-quinze centimes");
    }

    @Test
    void montant_negatif_refuse() {
        assertThatThrownBy(() -> ReglesFacture.montantEnLettres(new BigDecimal("-1")))
                .isInstanceOf(RegleMetierException.class);
    }
}
