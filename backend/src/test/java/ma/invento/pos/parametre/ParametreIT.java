package ma.invento.pos.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import ma.invento.pos.IntegrationTest;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.journal.infrastructure.JournalActionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class ParametreIT extends IntegrationTest {

    @Autowired
    JournalActionRepository journal;

    @Test
    void plafonds_lisibles_par_la_caisse_modifiables_par_l_admin_et_journalises() throws Exception {
        String caissier = jeton(Role.CAISSIER);
        mvc.perform(get("/api/v1/parametres").header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuseauHoraire").value("Africa/Casablanca"));

        Map<String, Object> parametres = Map.of(
                "plafondRemiseCaissier", 15,
                "plafondRemiseResponsable", 30,
                "boutiqueNom", "Parapharmacie Test",
                "boutiqueAdresse", "Rabat",
                "fuseauHoraire", "Africa/Casablanca");
        mvc.perform(put("/api/v1/parametres")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(parametres)))
                .andExpect(status().isForbidden());

        String admin = jetonAdmin();
        mvc.perform(put("/api/v1/parametres")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(parametres)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plafondRemiseCaissier").value(15));
        assertThat(journal.findByAction(ActionJournal.PARAMETRE_MODIFIE))
                .anyMatch(a -> a.getDetail().equals("plafond_remise_caissier : 10 -> 15"));

        Map<String, Object> incoherents = new HashMap<>(parametres);
        incoherents.put("plafondRemiseCaissier", 40);
        mvc.perform(put("/api/v1/parametres")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(incoherents)))
                .andExpect(status().isUnprocessableEntity());

        Map<String, Object> fuseauInconnu = new HashMap<>(parametres);
        fuseauInconnu.put("fuseauHoraire", "Mars/Olympus");
        mvc.perform(put("/api/v1/parametres")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(fuseauInconnu)))
                .andExpect(status().isUnprocessableEntity());
    }
}
