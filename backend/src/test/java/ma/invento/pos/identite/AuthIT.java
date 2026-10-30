package ma.invento.pos.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.invento.pos.IntegrationTest;
import ma.invento.pos.identite.application.AdministrationAdminService;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.journal.infrastructure.JournalActionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class AuthIT extends IntegrationTest {

    private static final String COOKIE = "pos_refresh";

    @Autowired
    AdministrationAdminService administrationAdmin;

    @Autowired
    JournalActionRepository journal;

    @Test
    void connexion_renvoie_un_jeton_court_et_un_cookie_httponly() throws Exception {
        Utilisateur caissier = creerUtilisateur(Role.CAISSIER);

        connexion(caissier.getLogin().toUpperCase(), MDP_UTILISATEUR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.utilisateur.role").value("CAISSIER"))
                .andExpect(jsonPath("$.utilisateur.motDePasseHash").doesNotExist())
                .andExpect(cookie().httpOnly(COOKIE, true))
                .andExpect(cookie().secure(COOKIE, true))
                .andExpect(cookie().sameSite(COOKIE, "Strict"))
                .andExpect(cookie().path(COOKIE, "/api/v1/auth"));
    }

    @Test
    void identifiants_inconnus_ou_compte_desactive_refuses_sans_detail() throws Exception {
        connexion("inconnu-" + UUID.randomUUID(), MDP_UTILISATEUR)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Identifiant ou mot de passe incorrect."));

        Utilisateur desactive = creerUtilisateur(Role.CAISSIER);
        desactive.modifierProfil("Test", "Inactif", Role.CAISSIER, false, Set.of());
        utilisateurs.save(desactive);
        connexion(desactive.getLogin(), MDP_UTILISATEUR).andExpect(status().isUnauthorized());
    }

    @Test
    void verrouillage_apres_cinq_essais_rates_puis_deverrouillage_par_l_admin() throws Exception {
        Utilisateur caissier = creerUtilisateur(Role.CAISSIER);
        for (int i = 0; i < Utilisateur.ESSAIS_MAX; i++) {
            connexion(caissier.getLogin(), "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        }
        connexion(caissier.getLogin(), MDP_UTILISATEUR)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("verrouillé")));
        assertThat(journal.findByAction(ActionJournal.COMPTE_VERROUILLE))
                .anyMatch(a -> caissier.getId().equals(a.getUtilisateurId()));

        mvc.perform(post("/api/v1/utilisateurs/" + caissier.getId() + "/deverrouiller")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jetonAdmin())))
                .andExpect(status().isNoContent());
        connexion(caissier.getLogin(), MDP_UTILISATEUR).andExpect(status().isOk());
    }

    @Test
    void mot_de_passe_temporaire_a_changer_avant_toute_autre_action() throws Exception {
        Utilisateur nouveau = utilisateurs.save(new Utilisateur(
                "Nouveau",
                "Caissier",
                "nouveau-" + UUID.randomUUID().toString().substring(0, 8),
                Role.CAISSIER,
                encoder.encode("Temporaire-1"),
                Instant.now()));
        String temporaire = lire(connexion(nouveau.getLogin(), "Temporaire-1")
                        .andExpect(
                                jsonPath("$.utilisateur.doitChangerMotDePasse").value(true))
                        .andReturn())
                .get("accessToken")
                .asText();

        mvc.perform(get("/api/v1/produits").header(HttpHeaders.AUTHORIZATION, bearer(temporaire)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/moi").header(HttpHeaders.AUTHORIZATION, bearer(temporaire)))
                .andExpect(status().isOk());

        changerMotDePasse(temporaire, "Temporaire-1", "court").andExpect(status().isUnprocessableEntity());
        changerMotDePasse(temporaire, "faux-actuel", "Nouveau-Secret-1").andExpect(status().isUnauthorized());
        String definitif = lire(changerMotDePasse(temporaire, "Temporaire-1", "Nouveau-Secret-1")
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.utilisateur.doitChangerMotDePasse").value(false))
                        .andReturn())
                .get("accessToken")
                .asText();
        mvc.perform(get("/api/v1/produits").header(HttpHeaders.AUTHORIZATION, bearer(definitif)))
                .andExpect(status().isOk());
    }

    @Test
    void rafraichissement_tournant_et_detection_de_reutilisation() throws Exception {
        Utilisateur caissier = creerUtilisateur(Role.CAISSIER);
        Cookie premier = connexion(caissier.getLogin(), MDP_UTILISATEUR)
                .andReturn()
                .getResponse()
                .getCookie(COOKIE);

        MvcResult rafraichi = mvc.perform(post("/api/v1/auth/rafraichir").cookie(premier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = rafraichi.getResponse().getCookie(COOKIE);
        assertThat(second.getValue()).isNotEqualTo(premier.getValue());

        // L'ancien jeton rejoué : vol présumé, toutes les sessions sont fermées.
        mvc.perform(post("/api/v1/auth/rafraichir").cookie(premier)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/rafraichir").cookie(second)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/rafraichir")).andExpect(status().isUnauthorized());
    }

    @Test
    void deconnexion_revoque_le_jeton_de_rafraichissement() throws Exception {
        Utilisateur caissier = creerUtilisateur(Role.CAISSIER);
        Cookie cookie = connexion(caissier.getLogin(), MDP_UTILISATEUR)
                .andReturn()
                .getResponse()
                .getCookie(COOKIE);

        mvc.perform(post("/api/v1/auth/deconnexion").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE, 0));
        mvc.perform(post("/api/v1/auth/rafraichir").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void code_de_secours_a_usage_unique_redefinit_le_mot_de_passe_admin() throws Exception {
        String code = lire(mvc.perform(post("/api/v1/auth/code-secours")
                                .header(HttpHeaders.AUTHORIZATION, bearer(jetonAdmin())))
                        .andExpect(status().isOk())
                        .andReturn())
                .get("codeSecours")
                .asText();

        secours("AAAAA-BBBBB-CCCCC-DDDDD", MDP_ADMIN).andExpect(status().isUnauthorized());
        String nouveauCode = lire(secours(code.toLowerCase(), MDP_ADMIN)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.login").value(AdministrationAdminService.LOGIN_ADMIN))
                        .andReturn())
                .get("nouveauCodeSecours")
                .asText();
        assertThat(nouveauCode).isNotEqualTo(code);
        secours(code, MDP_ADMIN).andExpect(status().isUnauthorized()); // déjà utilisé

        connexion(AdministrationAdminService.LOGIN_ADMIN, MDP_ADMIN).andExpect(status().isOk());
        assertThat(journal.findByAction(ActionJournal.CODE_SECOURS_UTILISE)).isNotEmpty();
        mvc.perform(post("/api/v1/auth/code-secours")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jeton(Role.RESPONSABLE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void commande_reset_admin_impose_un_mot_de_passe_temporaire() throws Exception {
        jetonAdmin();
        var secrets = administrationAdmin.reinitialiserAdmin();

        connexion(secrets.login(), MDP_ADMIN).andExpect(status().isUnauthorized());
        String temporaire = lire(connexion(secrets.login(), secrets.motDePasseTemporaire())
                        .andExpect(
                                jsonPath("$.utilisateur.doitChangerMotDePasse").value(true))
                        .andReturn())
                .get("accessToken")
                .asText();
        assertThat(journal.findByAction(ActionJournal.REINITIALISATION_ADMIN_COMMANDE))
                .isNotEmpty();

        // Remet le mot de passe commun aux tests.
        changerMotDePasse(temporaire, secrets.motDePasseTemporaire(), MDP_ADMIN).andExpect(status().isOk());
    }

    @Test
    void installation_cree_un_seul_admin() throws Exception {
        jetonAdmin();
        assertThat(administrationAdmin.installerSiNecessaire()).isEmpty();
        assertThat(utilisateurs.findAll())
                .filteredOn(u -> u.getRole() == Role.ADMIN)
                .hasSize(1);
        assertThat(journal.findByAction(ActionJournal.INSTALLATION_ADMIN)).hasSize(1);
    }

    private ResultActions changerMotDePasse(String jeton, String actuel, String nouveau) throws Exception {
        return mvc.perform(post("/api/v1/auth/mot-de-passe")
                .header(HttpHeaders.AUTHORIZATION, bearer(jeton))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("motDePasseActuel", actuel, "nouveauMotDePasse", nouveau))));
    }

    private ResultActions secours(String code, String nouveau) throws Exception {
        return mvc.perform(post("/api/v1/auth/secours")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("codeSecours", code, "nouveauMotDePasse", nouveau))));
    }
}
