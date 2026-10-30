package ma.invento.pos.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.invento.pos.IntegrationTest;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.journal.infrastructure.JournalActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Gestion des comptes et des caisses par l'admin. */
class UtilisateurIT extends IntegrationTest {

    @Autowired
    JournalActionRepository journal;

    private String admin;
    private String suffixe;

    @BeforeEach
    void preparer() throws Exception {
        admin = jetonAdmin();
        suffixe = UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void admin_cree_une_caisse_et_un_caissier_qui_doit_changer_son_mot_de_passe() throws Exception {
        long caisseId = creerCaisse("Caisse " + suffixe);

        JsonNode cree = lire(mvc.perform(post("/api/v1/utilisateurs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json.writeValueAsString(creation("sara-" + suffixe, Role.CAISSIER, List.of(caisseId)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doitChangerMotDePasse").value(true))
                .andExpect(jsonPath("$.caisseIds[0]").value(caisseId))
                .andReturn());

        connexion("sara-" + suffixe, "Temporaire-2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utilisateur.doitChangerMotDePasse").value(true));

        mvc.perform(post("/api/v1/utilisateurs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(creation("SARA-" + suffixe, Role.CAISSIER, List.of()))))
                .andExpect(status().isConflict());

        assertThat(journal.findByAction(ActionJournal.UTILISATEUR_CREE))
                .anyMatch(a -> a.getDetail().contains("sara-" + suffixe));
        assertThat(cree.get("id").asLong()).isPositive();

        mvc.perform(put("/api/v1/caisses/" + caisseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nom", "Caisse renommée " + suffixe, "active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void un_seul_admin_qui_ne_peut_etre_ni_retrograde_ni_desactive() throws Exception {
        mvc.perform(post("/api/v1/utilisateurs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(creation("admin2-" + suffixe, Role.ADMIN, List.of()))))
                .andExpect(status().isUnprocessableEntity());

        JsonNode moi = lire(mvc.perform(get("/api/v1/auth/moi").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andReturn());
        Map<String, Object> modification = new HashMap<>();
        modification.put("nom", "Administrateur");
        modification.put("prenom", "");
        modification.put("role", "CAISSIER");
        modification.put("actif", true);
        modification.put("caisseIds", List.of());
        modification.put("version", moi.get("version").asLong());
        mvc.perform(put("/api/v1/utilisateurs/" + moi.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(modification)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void desactiver_un_compte_le_garde_et_bloque_sa_connexion() throws Exception {
        var responsable = creerUtilisateur(Role.RESPONSABLE);
        Map<String, Object> modification = new HashMap<>();
        modification.put("nom", "Test");
        modification.put("prenom", "Responsable");
        modification.put("role", "RESPONSABLE");
        modification.put("actif", false);
        modification.put("caisseIds", List.of());
        modification.put("version", responsable.getVersion());

        mvc.perform(put("/api/v1/utilisateurs/" + responsable.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(modification)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(false));

        connexion(responsable.getLogin(), MDP_UTILISATEUR).andExpect(status().isUnauthorized());
        assertThat(utilisateurs.findById(responsable.getId())).isPresent();
    }

    @Test
    void reinitialisation_du_mot_de_passe_et_pin() throws Exception {
        var caissier = creerUtilisateur(Role.CAISSIER);

        mvc.perform(post("/api/v1/utilisateurs/" + caissier.getId() + "/mot-de-passe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("motDePasseTemporaire", "Provisoire-99"))))
                .andExpect(status().isNoContent());
        connexion(caissier.getLogin(), "Provisoire-99")
                .andExpect(jsonPath("$.utilisateur.doitChangerMotDePasse").value(true));

        mvc.perform(put("/api/v1/utilisateurs/" + caissier.getId() + "/pin")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"12\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/utilisateurs/" + caissier.getId() + "/pin")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"0420\"}"))
                .andExpect(status().isNoContent());
        assertThat(utilisateurs.findById(caissier.getId()).orElseThrow().isPinDefini())
                .isTrue();
    }

    @Test
    void ecrans_admin_interdits_aux_autres_roles() throws Exception {
        String responsable = jeton(Role.RESPONSABLE);
        mvc.perform(get("/api/v1/utilisateurs").header(HttpHeaders.AUTHORIZATION, bearer(responsable)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/journal").header(HttpHeaders.AUTHORIZATION, bearer(responsable)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/caisses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(responsable))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Pirate\",\"active\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/caisses").header(HttpHeaders.AUTHORIZATION, bearer(responsable)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/journal").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu").isArray());
    }

    private long creerCaisse(String nom) throws Exception {
        return lire(mvc.perform(post("/api/v1/caisses")
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("nom", nom, "active", true))))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asLong();
    }

    private static Map<String, Object> creation(String login, Role role, List<Long> caisseIds) {
        Map<String, Object> creation = new HashMap<>();
        creation.put("nom", "Alaoui");
        creation.put("prenom", "Sara");
        creation.put("login", login);
        creation.put("role", role.name());
        creation.put("motDePasseTemporaire", "Temporaire-2026");
        creation.put("caisseIds", caisseIds);
        return creation;
    }
}
