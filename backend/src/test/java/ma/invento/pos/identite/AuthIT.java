package ma.invento.pos.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.invento.pos.IntegrationTest;
import ma.invento.pos.identite.application.AdministrationAdminService;
import ma.invento.pos.identite.application.LimiteurEssais;
import ma.invento.pos.identite.config.LimitesEssaisProperties;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
import ma.invento.pos.identite.infrastructure.EssaisConnexionJdbc;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.journal.infrastructure.JournalActionRepository;
import ma.invento.pos.shared.domain.TropDEssaisException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

class AuthIT extends IntegrationTest {

    private static final String COOKIE = "pos_refresh";
    private static final String VERROUILLE = "Compte verrouillé après 5 essais ratés. Réessayez dans 15 min"
            + " ou demandez à l'administrateur de le débloquer.";

    @Autowired
    AdministrationAdminService administrationAdmin;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EssaisConnexionJdbc essaisConnexion;

    @Autowired
    TransactionTemplate transactions;

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
        connexion(caissier.getLogin(), "mauvais-mot-de-passe")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Identifiant ou mot de passe incorrect."));
        connexion(caissier.getLogin(), "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        connexion(caissier.getLogin(), "mauvais-mot-de-passe")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(containsString("Il vous reste 2 essais")));
        connexion(caissier.getLogin(), "mauvais-mot-de-passe")
                .andExpect(jsonPath("$.detail").value(containsString("Il vous reste 1 essai ")));
        connexion(caissier.getLogin(), "mauvais-mot-de-passe")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(VERROUILLE));
        // Verrouillé : même réponse, que le mot de passe soit juste ou faux.
        connexion(caissier.getLogin(), "mauvais-mot-de-passe")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(VERROUILLE));
        connexion(caissier.getLogin(), MDP_UTILISATEUR)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(VERROUILLE));
        assertThat(journal.findByAction(ActionJournal.COMPTE_VERROUILLE))
                .anyMatch(a -> caissier.getId().equals(a.getUtilisateurId()));

        mvc.perform(post("/api/v1/utilisateurs/" + caissier.getId() + "/deverrouiller")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jetonAdmin())))
                .andExpect(status().isNoContent());
        connexion(caissier.getLogin(), MDP_UTILISATEUR).andExpect(status().isOk());
    }

    @Test
    void verrouillage_leve_tout_seul_apres_quinze_minutes() throws Exception {
        Utilisateur caissier = creerUtilisateur(Role.CAISSIER);
        for (int i = 0; i < Utilisateur.ESSAIS_MAX; i++) connexion(caissier.getLogin(), "mauvais-mot-de-passe");
        connexion(caissier.getLogin(), MDP_UTILISATEUR).andExpect(status().isForbidden());
        assertThat(utilisateurs.findById(caissier.getId()).orElseThrow().getVerrouilleJusqua())
                .isBetween(
                        Instant.now().plus(Duration.ofMinutes(14)),
                        Instant.now().plus(Duration.ofMinutes(16)));

        // Fin du verrouillage atteinte : le compteur repart de zéro.
        expirerVerrouillages("utilisateur", "id = " + caissier.getId());
        connexion(caissier.getLogin(), "mauvais-mot-de-passe")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Identifiant ou mot de passe incorrect."));
        connexion(caissier.getLogin(), MDP_UTILISATEUR).andExpect(status().isOk());
        Utilisateur relu = utilisateurs.findById(caissier.getId()).orElseThrow();
        assertThat(relu.getEssaisRates()).isZero();
        assertThat(relu.getVerrouilleJusqua()).isNull();
    }

    @Test
    void identifiant_inconnu_recoit_les_memes_reponses_qu_un_vrai_compte() throws Exception {
        String inconnu = "fantome." + UUID.randomUUID().toString().substring(0, 8);
        connexion(inconnu, "x").andExpect(status().isUnauthorized());
        connexion(inconnu.toUpperCase(), "x").andExpect(status().isUnauthorized());
        connexion(inconnu, "x")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(containsString("Il vous reste 2 essais")));
        connexion(inconnu, "x").andExpect(status().isUnauthorized());
        connexion(inconnu, "x")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(VERROUILLE));
        connexion(inconnu, "x").andExpect(status().isForbidden());

        // Comme un vrai compte, le verrouillage se lève tout seul et le compteur repart de zéro.
        expirerVerrouillages("essai_identifiant_inconnu", "verrouille_jusqua > now()");
        connexion(inconnu, "x")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Identifiant ou mot de passe incorrect."));
    }

    /** Ramène dans le passé la fin des verrouillages choisis (simule l'attente de 15 minutes). */
    private void expirerVerrouillages(String table, String condition) {
        jdbc.update("update pos." + table + " set verrouille_jusqua = now() - interval '1 minute' where " + condition);
    }

    @Test
    void essais_rates_plafonnes_par_adresse_ip() {
        var limiteur = new LimiteurEssais(
                essaisConnexion,
                new LimitesEssaisProperties(2, Duration.ofMinutes(15), 1, Duration.ofHours(1)),
                Clock.systemUTC());
        String ip = "203.0.113." + (int) (Math.random() * 250);
        transactions.executeWithoutResult(t -> {
            limiteur.verifier(limiteur.connexion(), ip);
            limiteur.enregistrerEchec(limiteur.connexion(), ip);
            limiteur.enregistrerEchec(limiteur.connexion(), ip);
        });
        assertThatThrownBy(() -> transactions.executeWithoutResult(t -> limiteur.verifier(limiteur.connexion(), ip)))
                .isInstanceOf(TropDEssaisException.class)
                .hasMessageContaining("Réessayez dans");
        // Une autre adresse et un autre type d'essai ont leurs propres compteurs.
        transactions.executeWithoutResult(t -> {
            limiteur.verifier(limiteur.connexion(), ip + "1");
            limiteur.verifier(limiteur.secours(), ip);
        });
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
