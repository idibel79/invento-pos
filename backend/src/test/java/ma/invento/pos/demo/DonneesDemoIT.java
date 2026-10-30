package ma.invento.pos.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ma.invento.pos.IntegrationTest;
import ma.invento.pos.caisse.infrastructure.CaisseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

/** Profil {@code demo} : base prête pour prévisualiser une PR, sans changement de mot de passe. */
@ActiveProfiles({"test", "demo"})
class DonneesDemoIT extends IntegrationTest {

    @Autowired
    DonneesDemo donneesDemo;

    @Autowired
    CaisseRepository caisses;

    @Test
    void comptes_caisses_et_produits_prets_a_l_emploi() throws Exception {
        for (String login : new String[] {"admin", "responsable", "caissier"}) {
            connexion(login, DonneesDemo.MOT_DE_PASSE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.utilisateur.doitChangerMotDePasse").value(false));
        }
        String caissier = jeton("caissier", DonneesDemo.MOT_DE_PASSE);

        mvc.perform(get("/api/v1/produits/code-barre/6118000010019")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockDisponible").value(120));
        mvc.perform(get("/api/v1/produits/code-barre/6118000010088")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enRupture").value(true));
        assertThat(caisses.count()).isEqualTo(2);
        mvc.perform(get("/api/v1/utilisateurs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jeton("admin", DonneesDemo.MOT_DE_PASSE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.login == 'admin')].caisseIds[*]", hasSize(2)));
    }

    @Test
    void rien_n_est_recree_si_la_base_contient_deja_une_caisse() {
        donneesDemo.creerSiBaseVide();

        assertThat(caisses.count()).isEqualTo(2);
    }
}
