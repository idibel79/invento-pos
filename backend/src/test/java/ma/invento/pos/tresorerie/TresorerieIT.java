package ma.invento.pos.tresorerie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ma.invento.pos.IntegrationTest;
import ma.invento.pos.catalogue.domain.Tva;
import ma.invento.pos.catalogue.infrastructure.TvaRepository;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.identite.domain.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** Caisse principale, comptes des caissiers, versements et clôture sur un vrai PostgreSQL. */
class TresorerieIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TvaRepository tvaRepository;

    private String admin;
    private long caisseId;
    private Utilisateur caissier;
    private String jetonCaissier;

    @BeforeEach
    void preparer() throws Exception {
        admin = jetonAdmin();
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        caisseId = creer("/api/v1/caisses", Map.of("nom", "Trésorerie " + suffixe, "active", true));
        caissier = creerUtilisateur(Role.CAISSIER);
        caissier.modifierProfil(caissier.getNom(), caissier.getPrenom(), Role.CAISSIER, true, Set.of(caisseId));
        utilisateurs.save(caissier);
        jetonCaissier = jeton(caissier.getLogin(), MDP_UTILISATEUR);
        operation("/api/v1/tresorerie/principale/alimentation", "5000", "Fonds de test")
                .andExpect(status().isNoContent());
    }

    @Test
    void versement_de_la_caisse_principale_en_transit_puis_valide_une_seule_fois() throws Exception {
        BigDecimal avant = soldePrincipale();
        long id = verserAuCaissier("300");
        assertThat(soldePrincipale()).isEqualByComparingTo(avant.subtract(new BigDecimal("300")));
        assertThat(soldeCaissier()).isEqualByComparingTo("0");

        // Seul le destinataire valide ; un autre caissier est refusé.
        mvc.perform(post("/api/v1/tresorerie/versements/" + id + "/validation")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jeton(Role.CAISSIER))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tresorerie/versements/" + id + "/validation")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));
        mvc.perform(post("/api/v1/tresorerie/versements/" + id + "/validation")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier)))
                .andExpect(status().isConflict());
        assertThat(soldeCaissier()).isEqualByComparingTo("300");
    }

    @Test
    void deux_validations_simultanees_ne_creditent_qu_une_fois() throws Exception {
        long id = verserAuCaissier("200");
        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> resultats = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Callable<Integer> validation = () -> {
                depart.await();
                return mvc.perform(post("/api/v1/tresorerie/versements/" + id + "/validation")
                                .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            resultats.add(pool.submit(validation));
        }
        depart.countDown();
        List<Integer> statuts = new ArrayList<>();
        for (Future<Integer> resultat : resultats) statuts.add(resultat.get());
        pool.shutdown();

        assertThat(statuts).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuts).filteredOn(s -> s == 409).hasSize(3);
        assertThat(soldeCaissier()).isEqualByComparingTo("200");
    }

    @Test
    void refus_et_annulation_rendent_l_argent_a_l_emetteur() throws Exception {
        BigDecimal avant = soldePrincipale();
        long refuse = verserAuCaissier("150");
        mvc.perform(post("/api/v1/tresorerie/versements/" + refuse + "/refus")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("motif", "Montant erroné"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSE"))
                .andExpect(jsonPath("$.motif").value("Montant erroné"));
        assertThat(soldePrincipale()).isEqualByComparingTo(avant);

        valider(verserAuCaissier("400"));
        long annule = verserALaPrincipale("100").get("id").asLong();
        assertThat(soldeCaissier()).isEqualByComparingTo("300");
        // Seul l'émetteur annule.
        mvc.perform(post("/api/v1/tresorerie/versements/" + annule + "/annulation")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tresorerie/versements/" + annule + "/annulation")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULE"));
        assertThat(soldeCaissier()).isEqualByComparingTo("400");
    }

    @Test
    void journal_de_mon_compte_ne_montre_que_mes_propres_versements() throws Exception {
        valider(verserAuCaissier("400"));
        verserALaPrincipale("100");
        // Un versement à un autre caissier n'apparaît pas dans mon journal.
        Utilisateur autre = creerUtilisateur(Role.CAISSIER);
        mvc.perform(post("/api/v1/tresorerie/principale/versements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("utilisateurId", autre.getId(), "montant", new BigDecimal("50")))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/tresorerie/mon-compte/journal")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.contenu[0].type").value("VERSEMENT_ENVOYE"))
                .andExpect(jsonPath("$.contenu[0].versLaPrincipale").value(true))
                .andExpect(jsonPath("$.contenu[0].statut").value("EN_TRANSIT"))
                .andExpect(jsonPath("$.contenu[1].versLaPrincipale").value(false))
                .andExpect(jsonPath("$.contenu[1].statut").value("VALIDE"));
        // Le journal de la caisse principale reste réservé à l'admin.
        mvc.perform(get("/api/v1/tresorerie/journal").header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier)))
                .andExpect(status().isForbidden());
    }

    @Test
    void solde_insuffisant_refuse_versement_retrait_fond_et_desactivation() throws Exception {
        valider(verserAuCaissier("100"));
        mvc.perform(post("/api/v1/tresorerie/mon-compte/versements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("montant", new BigDecimal("100.01")))))
                .andExpect(status().isUnprocessableEntity());
        operation(
                        "/api/v1/tresorerie/principale/retrait",
                        soldePrincipale().add(BigDecimal.ONE).toPlainString(),
                        "Banque")
                .andExpect(status().isUnprocessableEntity());
        operation("/api/v1/tresorerie/principale/retrait", "1", "").andExpect(status().isUnprocessableEntity());
        ouvrir("100.01").andExpect(status().isUnprocessableEntity());

        // Un utilisateur qui doit encore de l'argent ne peut pas être désactivé.
        Map<String, Object> modification = new HashMap<>();
        modification.put("nom", caissier.getNom());
        modification.put("prenom", caissier.getPrenom());
        modification.put("login", caissier.getLogin());
        modification.put("role", "CAISSIER");
        modification.put("actif", false);
        modification.put("caisseIds", List.of(caisseId));
        modification.put(
                "version", utilisateurs.findById(caissier.getId()).orElseThrow().getVersion());
        modification.put("seuilsRemise", Map.of("ligne", 0, "ticket", 0));
        mvc.perform(put("/api/v1/utilisateurs/" + caissier.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(modification)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cloture_avec_manque_puis_excedent_et_monnaie_insuffisante() throws Exception {
        valider(verserAuCaissier("100"));
        long produit = produit("50.00");

        // Fond de 0 : impossible de rendre la monnaie sur un billet de 200.
        ouvrir("0").andExpect(status().isCreated());
        long session0 = sessionCourante();
        vendre(produit, "200")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(startsWith("Monnaie insuffisante dans le tiroir")));
        vendre(produit, "50").andExpect(status().isCreated());
        // Manque de 10 : attendu 50, compté 40, tout versé. Le solde ne baisse pas du manque.
        cloturer(session0, "40", "40")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ecart").value(-10.0));
        assertThat(soldeCaissier()).isEqualByComparingTo("110"); // 100 + 50 ventes − 40 versés

        // Excédent de 5 : il s'ajoute au solde ; rien n'est versé.
        ouvrir("100").andExpect(status().isCreated());
        long session1 = sessionCourante();
        vendre(produit, "100").andExpect(status().isCreated());
        cloturer(session1, "155", "0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ecart").value(5.0));
        assertThat(soldeCaissier()).isEqualByComparingTo("165"); // 110 + 50 + 5

        // Versé supérieur au compté : refusé.
        ouvrir("0").andExpect(status().isCreated());
        cloturer(sessionCourante(), "10", "20").andExpect(status().isUnprocessableEntity());
    }

    // ---------------------------------------------------------------- Aides

    private ResultActions operation(String chemin, String montant, String motif) throws Exception {
        return mvc.perform(post(chemin)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("montant", new BigDecimal(montant), "motif", motif))));
    }

    private long verserAuCaissier(String montant) throws Exception {
        return lire(mvc.perform(post("/api/v1/tresorerie/principale/versements")
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(
                                        Map.of("utilisateurId", caissier.getId(), "montant", new BigDecimal(montant)))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.statut").value("EN_TRANSIT"))
                        .andReturn())
                .get("id")
                .asLong();
    }

    private com.fasterxml.jackson.databind.JsonNode verserALaPrincipale(String montant) throws Exception {
        return lire(mvc.perform(post("/api/v1/tresorerie/mon-compte/versements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("montant", new BigDecimal(montant)))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void valider(long versementId) throws Exception {
        mvc.perform(post("/api/v1/tresorerie/versements/" + versementId + "/validation")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier)))
                .andExpect(status().isOk());
    }

    private ResultActions ouvrir(String fond) throws Exception {
        return mvc.perform(post("/api/v1/sessions")
                .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("caisseId", caisseId, "fondInitial", new BigDecimal(fond)))));
    }

    private long sessionCourante() throws Exception {
        return lire(mvc.perform(get("/api/v1/sessions/courante")
                                .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier)))
                        .andExpect(status().isOk())
                        .andReturn())
                .get("id")
                .asLong();
    }

    private ResultActions cloturer(long sessionId, String compte, String verse) throws Exception {
        return mvc.perform(post("/api/v1/sessions/" + sessionId + "/cloture")
                .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "montantCompteEspeces",
                        new BigDecimal(compte),
                        "montantCompteTpe",
                        BigDecimal.ZERO,
                        "montantVerse",
                        new BigDecimal(verse)))));
    }

    private ResultActions vendre(long produit, String recu) throws Exception {
        Map<String, Object> ligne = new HashMap<>();
        ligne.put("produitId", produit);
        ligne.put("quantite", 1);
        return mvc.perform(post("/api/v1/ventes")
                .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                        Map.of("panier", Map.of("lignes", List.of(ligne)), "especesRecues", new BigDecimal(recu)))));
    }

    private long produit(String ppv) throws Exception {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        long tvaId = tvaRepository.findAllByOrderByTauxAsc().stream()
                .findFirst()
                .orElseGet(() -> tvaRepository.save(new Tva("TVA", new BigDecimal("20.00"))))
                .getId();
        Map<String, Object> commande = new HashMap<>();
        commande.put("pid", "T" + suffixe);
        commande.put("designation", "PRODUIT T" + suffixe);
        commande.put("categorieId", creer("/api/v1/categories", Map.of("nom", "Cat " + suffixe)));
        commande.put("zoneId", creer("/api/v1/zones", Map.of("nom", "Zone " + suffixe)));
        commande.put("tvaId", tvaId);
        commande.put("ppv", new BigDecimal(ppv));
        commande.put("actif", true);
        commande.put("codesBarres", List.of());
        long id = creer("/api/v1/produits", commande);
        jdbc.update("update pos.produit set stock = 100 where id = ?", id);
        return id;
    }

    private long creer(String chemin, Map<String, Object> corps) throws Exception {
        return lire(mvc.perform(post(chemin)
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(corps)))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asLong();
    }

    private BigDecimal soldePrincipale() {
        return jdbc.queryForObject(
                "select solde from pos.compte_tresorerie where type = 'PRINCIPALE'", BigDecimal.class);
    }

    private BigDecimal soldeCaissier() throws Exception {
        return new BigDecimal(lire(mvc.perform(get("/api/v1/tresorerie/mon-compte")
                                .header(HttpHeaders.AUTHORIZATION, bearer(jetonCaissier)))
                        .andExpect(status().isOk())
                        .andReturn())
                .get("solde")
                .asText());
    }
}
