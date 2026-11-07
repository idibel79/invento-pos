package ma.invento.pos.parametre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
    void parametres_lisibles_par_la_caisse_modifiables_par_l_admin_et_journalises() throws Exception {
        String caissier = jeton(Role.CAISSIER);
        mvc.perform(get("/api/v1/parametres").header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuseauHoraire").value("Africa/Casablanca"));

        String nom = "Parapharmacie " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> parametres = Map.of(
                "boutiqueNom", nom,
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
                .andExpect(jsonPath("$.boutiqueNom").value(nom));
        assertThat(journal.findByAction(ActionJournal.PARAMETRE_MODIFIE))
                .anyMatch(a -> a.getDetail().startsWith("boutique_nom")
                        && a.getDetail().contains(nom));

        Map<String, Object> fuseauInconnu = new HashMap<>(parametres);
        fuseauInconnu.put("fuseauHoraire", "Mars/Olympus");
        mvc.perform(put("/api/v1/parametres")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(fuseauInconnu)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void informations_du_ticket_enregistrees_et_controlees() throws Exception {
        String admin = jetonAdmin();
        Map<String, Object> parametres = new HashMap<>(Map.of(
                "boutiqueNom", "Parapharmacie Test",
                "boutiqueAdresse", "Rabat",
                "boutiqueTelephone", " 05 37 00 00 00 ",
                "boutiqueEmail", "contact@boutique.ma",
                "ticketMessage", "Merci de votre visite",
                "fuseauHoraire", "Africa/Casablanca"));
        mvc.perform(put("/api/v1/parametres")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(parametres)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boutiqueTelephone").value("05 37 00 00 00"))
                .andExpect(jsonPath("$.boutiqueEmail").value("contact@boutique.ma"))
                .andExpect(jsonPath("$.ticketMessage").value("Merci de votre visite"));

        mvc.perform(get("/api/v1/parametres").header(HttpHeaders.AUTHORIZATION, bearer(jeton(Role.CAISSIER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketMessage").value("Merci de votre visite"));

        Map<String, Object> emailInvalide = new HashMap<>(parametres);
        emailInvalide.put("boutiqueEmail", "pas-un-email");
        mvc.perform(put("/api/v1/parametres")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(emailInvalide)))
                .andExpect(status().isBadRequest());

        Map<String, Object> telephoneInvalide = new HashMap<>(parametres);
        telephoneInvalide.put("boutiqueTelephone", "appelez-nous");
        mvc.perform(put("/api/v1/parametres")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(telephoneInvalide)))
                .andExpect(status().isBadRequest());
    }
}
