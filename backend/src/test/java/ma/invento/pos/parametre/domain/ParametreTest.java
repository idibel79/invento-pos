package ma.invento.pos.parametre.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ParametreTest {

    @Test
    void cle_en_base_et_valeur_modifiable() {
        Parametre parametre = new Parametre(CleParametre.FUSEAU_HORAIRE.cle(), "Africa/Casablanca");
        parametre.setValeur("Europe/Paris");
        assertThat(parametre.getCle()).isEqualTo("fuseau_horaire");
        assertThat(parametre.getValeur()).isEqualTo("Europe/Paris");
    }
}
