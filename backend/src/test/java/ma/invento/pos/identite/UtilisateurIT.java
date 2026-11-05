package ma.invento.pos.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.ResultActions;

/** Gestion des comptes et des caisses par l'admin. */
class UtilisateurIT extends IntegrationTest {

    private static final Map<String, Object> AUCUN_SEUIL = Map.of("ligne", 0, "ticket", 0);

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
    void une_caisse_attribuee_a_un_seul_utilisateur_et_une_seule_caisse_par_utilisateur() throws Exception {
        long caisseA = creerCaisse("Caisse A " + suffixe);
        long caisseB = creerCaisse("Caisse B " + suffixe);
        long caisseC = creerCaisse("Caisse C " + suffixe);
        mvc.perform(post("/api/v1/utilisateurs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(creation("a-" + suffixe, Role.CAISSIER, List.of(caisseA)))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/utilisateurs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(creation("b-" + suffixe, Role.CAISSIER, List.of(caisseA)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cette caisse est déjà attribuée à Sara Alaoui."));
        mvc.perform(post("/api/v1/utilisateurs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                creation("c-" + suffixe, Role.CAISSIER, List.of(caisseB, caisseC)))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void supprimer_une_caisse_inactive_la_retire_des_listes_et_des_attributions() throws Exception {
        String nom = "Caisse D " + suffixe;
        long caisseId = creerCaisse(nom);
        long sara = lire(mvc.perform(post("/api/v1/utilisateurs")
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(
                                        creation("d-" + suffixe, Role.CAISSIER, List.of(caisseId)))))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asLong();

        mvc.perform(delete("/api/v1/caisses/" + caisseId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/v1/caisses/" + caisseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nom", nom, "active", false))))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/caisses/" + caisseId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/caisses").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$[?(@.id == " + caisseId + ")]").isEmpty());
        mvc.perform(get("/api/v1/utilisateurs").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$[?(@.id == " + sara + ")].caisseIds[*]").isEmpty());
        assertThat(journal.findByAction(ActionJournal.CAISSE_SUPPRIMEE))
                .anyMatch(a -> a.getDetail().equals(nom));
        // Le nom redevient disponible.
        creerCaisse(nom);
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
        modification.put("login", moi.get("login").asText());
        modification.put("role", "CAISSIER");
        modification.put("actif", true);
        modification.put("caisseIds", List.of());
        modification.put("seuilsRemise", AUCUN_SEUIL);
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
        modification.put("login", responsable.getLogin());
        modification.put("role", "RESPONSABLE");
        modification.put("actif", false);
        modification.put("caisseIds", List.of());
        modification.put("seuilsRemise", AUCUN_SEUIL);
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
    void modifier_identifiant_mot_de_passe_et_pin_en_une_fois() throws Exception {
        var caissier = creerUtilisateur(Role.RESPONSABLE);
        var autre = creerUtilisateur(Role.CAISSIER);
        String nouveauLogin = "nouveau-" + suffixe;
        Map<String, Object> modification = new HashMap<>();
        modification.put("nom", caissier.getNom());
        modification.put("prenom", caissier.getPrenom());
        modification.put("login", nouveauLogin);
        modification.put("role", "RESPONSABLE");
        modification.put("actif", true);
        modification.put("caisseIds", List.of());
        modification.put("seuilsRemise", Map.of("ligne", 10, "ticket", 5));
        modification.put("version", caissier.getVersion());

        modification.put("pin", "12");
        modifier(caissier.getId(), modification).andExpect(status().isBadRequest());
        modification.put("pin", "0420");
        modification.put("login", autre.getLogin().toUpperCase());
        modifier(caissier.getId(), modification).andExpect(status().isConflict());
        modification.put("login", nouveauLogin);
        modification.put("motDePasseTemporaire", nouveauLogin);
        modifier(caissier.getId(), modification).andExpect(status().isUnprocessableEntity());

        modification.put("motDePasseTemporaire", "Provisoire-99");
        modifier(caissier.getId(), modification)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value(nouveauLogin))
                .andExpect(jsonPath("$.pinDefini").value(true))
                .andExpect(jsonPath("$.seuilsRemise.ligne").value(10))
                .andExpect(jsonPath("$.seuilsRemise.ticket").value(5))
                .andExpect(jsonPath("$.doitChangerMotDePasse").value(true));

        connexion(caissier.getLogin(), "Provisoire-99").andExpect(status().isUnauthorized());
        connexion(nouveauLogin, "Provisoire-99")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utilisateur.doitChangerMotDePasse").value(true));
        assertThat(journal.findByAction(ActionJournal.UTILISATEUR_MODIFIE))
                .anyMatch(a -> a.getDetail().startsWith(caissier.getLogin() + " → " + nouveauLogin));
        assertThat(journal.findByAction(ActionJournal.PIN_DEFINI))
                .anyMatch(a -> a.getDetail().equals(nouveauLogin));
    }

    @Test
    void un_caissier_n_a_ni_pin_ni_seuils_de_remise() throws Exception {
        var responsable = creerUtilisateur(Role.RESPONSABLE);
        Map<String, Object> modification = new HashMap<>();
        modification.put("nom", responsable.getNom());
        modification.put("prenom", responsable.getPrenom());
        modification.put("login", responsable.getLogin());
        modification.put("pin", "0420");
        modification.put("role", "RESPONSABLE");
        modification.put("actif", true);
        modification.put("caisseIds", List.of());
        modification.put("seuilsRemise", Map.of("ligne", 101, "ticket", 5));
        modification.put("version", responsable.getVersion());
        modifier(responsable.getId(), modification).andExpect(status().isBadRequest());

        modification.put("seuilsRemise", Map.of("ligne", 10, "ticket", 5));
        long version = lire(modifier(responsable.getId(), modification)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.pinDefini").value(true))
                        .andReturn())
                .get("version")
                .asLong();

        // Redevenu caissier : PIN et seuils retirés.
        modification.remove("pin");
        modification.put("role", "CAISSIER");
        modification.put("version", version);
        version = lire(modifier(responsable.getId(), modification)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.pinDefini").value(false))
                        .andExpect(jsonPath("$.seuilsRemise.ligne").value(0))
                        .andReturn())
                .get("version")
                .asLong();

        modification.put("pin", "0420");
        modification.put("version", version);
        modifier(responsable.getId(), modification).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void modifier_sans_mot_de_passe_ni_pin_les_garde() throws Exception {
        var caissier = creerUtilisateur(Role.CAISSIER);
        Map<String, Object> modification = new HashMap<>();
        modification.put("nom", "Renommé");
        modification.put("prenom", caissier.getPrenom());
        modification.put("login", caissier.getLogin());
        modification.put("motDePasseTemporaire", "");
        modification.put("role", "CAISSIER");
        modification.put("actif", true);
        modification.put("caisseIds", List.of());
        modification.put("seuilsRemise", AUCUN_SEUIL);
        modification.put("version", caissier.getVersion());

        modifier(caissier.getId(), modification).andExpect(status().isOk());
        connexion(caissier.getLogin(), MDP_UTILISATEUR).andExpect(status().isOk());
    }

    private ResultActions modifier(long id, Map<String, Object> modification) throws Exception {
        return mvc.perform(put("/api/v1/utilisateurs/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(modification)));
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
        creation.put("seuilsRemise", AUCUN_SEUIL);
        return creation;
    }
}
