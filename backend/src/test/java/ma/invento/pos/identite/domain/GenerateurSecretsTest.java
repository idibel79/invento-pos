package ma.invento.pos.identite.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GenerateurSecretsTest {

    private final GenerateurSecrets generateur = new GenerateurSecrets();

    @Test
    void code_de_secours_lisible_en_quatre_groupes() {
        String code = generateur.codeSecours();
        assertThat(code).matches("[A-HJ-NP-Z2-9]{5}(-[A-HJ-NP-Z2-9]{5}){3}");
        assertThat(generateur.codeSecours()).isNotEqualTo(code);
    }

    @Test
    void mot_de_passe_temporaire_conforme_a_la_politique() {
        String motDePasse = generateur.motDePasseTemporaire();
        assertThat(motDePasse).hasSize(14);
        PolitiqueSecrets.verifierMotDePasse("admin", motDePasse);
    }

    @Test
    void jeton_aleatoire_et_empreinte_stable() {
        String jeton = generateur.jetonRafraichissement();
        assertThat(jeton).hasSizeGreaterThanOrEqualTo(43).isNotEqualTo(generateur.jetonRafraichissement());
        assertThat(GenerateurSecrets.empreinte(jeton))
                .hasSize(64)
                .isEqualTo(GenerateurSecrets.empreinte(jeton))
                .isNotEqualTo(jeton);
    }
}
