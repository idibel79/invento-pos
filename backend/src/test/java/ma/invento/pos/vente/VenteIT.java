package ma.invento.pos.vente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
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
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.journal.infrastructure.JournalActionRepository;
import ma.invento.pos.stock.domain.TypeMouvement;
import ma.invento.pos.stock.infrastructure.MouvementStockRepository;
import ma.invento.pos.vente.application.VentesEnAttenteExpirees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** Vente, mise en attente et sessions de caisse sur un vrai PostgreSQL. */
class VenteIT extends IntegrationTest {

    private static final String PIN = "4821";

    @Autowired
    TvaRepository tvaRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MouvementStockRepository mouvements;

    @Autowired
    JournalActionRepository journalActions;

    @Autowired
    VentesEnAttenteExpirees ventesExpirees;

    private String admin;
    private String suffixe;
    private long caisseId;
    private long autreCaisseId;
    private long categorieId;
    private long zoneId;
    private long tvaId;

    @BeforeEach
    void preparer() throws Exception {
        admin = jetonAdmin();
        suffixe = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        caisseId = creer("/api/v1/caisses", Map.of("nom", "Caisse " + suffixe, "active", true));
        autreCaisseId = creer("/api/v1/caisses", Map.of("nom", "Autre " + suffixe, "active", true));
        categorieId = creer("/api/v1/categories", Map.of("nom", "Catégorie " + suffixe));
        zoneId = creer("/api/v1/zones", Map.of("nom", "Zone " + suffixe));
        // Plafonds par défaut (10 % / 30 %) : la base est partagée, ParametreIT les modifie.
        mvc.perform(put("/api/v1/parametres")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "plafondRemiseCaissier", 10,
                                "plafondRemiseResponsable", 30,
                                "boutiqueNom", "Parapharmacie Test",
                                "boutiqueAdresse", "Rabat",
                                "fuseauHoraire", "Africa/Casablanca"))))
                .andExpect(status().isOk());
        tvaId = tvaRepository.findAllByOrderByTauxAsc().stream()
                .filter(t -> t.getTaux().compareTo(BigDecimal.valueOf(20)) == 0)
                .findFirst()
                .orElseGet(() -> tvaRepository.save(new Tva("TVA", new BigDecimal("20.00"))))
                .getId();
    }

    @Test
    void vente_en_especes_decremente_le_stock_et_rend_la_monnaie_une_seule_fois() throws Exception {
        String caissier = caissierSur(caisseId);
        ouvrirSession(caissier, caisseId);
        long produit = produit("26.20", 5);

        JsonNode apercu = lire(mvc.perform(post("/api/v1/ventes/calcul")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(panier(ligne(produit, 2)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totaux.totalTtc").value(52.40))
                .andExpect(jsonPath("$.totaux.totalHt").value(43.67))
                .andExpect(jsonPath("$.autorisationRequise").value("AUCUNE"))
                .andExpect(jsonPath("$.stockSuffisant").value(true))
                .andReturn());
        assertThat(apercu.get("lignes").get(0).get("designation").asText()).startsWith("PRODUIT");

        String cle = UUID.randomUUID().toString();
        JsonNode vente = lire(valider(caissier, cle, panier(ligne(produit, 2)), "100.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.paiement.rendu").value(47.60))
                .andReturn());
        assertThat(vente.get("numero").asText()).startsWith("C" + caisseId + "-");

        // Renvoi après coupure réseau : même vente, stock décrémenté une seule fois.
        valider(caissier, cle, panier(ligne(produit, 2)), "100.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(vente.get("id").asLong()));
        assertThat(stock(produit)).isEqualTo(3);
        assertThat(mouvements.findByProduitIdOrderByIdAsc(produit))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.getType()).isEqualTo(TypeMouvement.VENTE);
                    assertThat(m.getQuantite()).isEqualTo(-2);
                    assertThat(m.getReference()).isEqualTo(vente.get("numero").asText());
                });
    }

    @Test
    void stock_insuffisant_bloque_toute_la_vente() throws Exception {
        String caissier = caissierSur(caisseId);
        ouvrirSession(caissier, caisseId);
        long disponible = produit("10.00", 5);
        long negatif = produit("10.00", -2);

        mvc.perform(post("/api/v1/ventes/calcul")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(panier(ligne(disponible, 1), ligne(negatif, 1)))))
                .andExpect(jsonPath("$.stockSuffisant").value(false))
                .andExpect(jsonPath("$.lignes[1].stockSuffisant").value(false));

        valider(caissier, UUID.randomUUID().toString(), panier(ligne(disponible, 1), ligne(negatif, 1)), "50")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Stock insuffisant")));
        valider(caissier, UUID.randomUUID().toString(), panier(ligne(disponible, 6)), "100")
                .andExpect(status().isUnprocessableEntity());

        // Annulation complète : la première ligne n'a pas été décrémentée.
        assertThat(stock(disponible)).isEqualTo(5);
        assertThat(mouvements.findByProduitIdOrderByIdAsc(disponible)).isEmpty();
    }

    @Test
    void montant_recu_insuffisant_et_vente_sans_session_refuses() throws Exception {
        String caissier = caissierSur(caisseId);
        long produit = produit("10.00", 5);
        valider(caissier, UUID.randomUUID().toString(), panier(ligne(produit, 1)), "10")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Ouvrez une session de caisse pour vendre."));

        ouvrirSession(caissier, caisseId);
        valider(caissier, UUID.randomUUID().toString(), panier(ligne(produit, 1)), "9.99")
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/ventes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("panier", panier(ligne(produit, 1)), "especesRecues", 10))))
                .andExpect(status().isBadRequest());
        assertThat(stock(produit)).isEqualTo(5);
    }

    @Test
    void vente_en_attente_reserve_le_stock_et_se_reprend_sur_la_meme_caisse() throws Exception {
        String caissier = caissierSur(caisseId);
        ouvrirSession(caissier, caisseId);
        long produit = produit("40.00", 3);

        Map<String, Object> panier = panier(remise(ligne(produit, 2), "POURCENT", "5"));
        JsonNode attente = lire(mvc.perform(post("/api/v1/ventes/attente")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("panier", panier, "note", "Parti au DAB"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.totaux.totalTtc").value(76.00))
                .andReturn());
        long venteId = attente.get("id").asLong();
        assertThat(stockReserve(produit)).isEqualTo(2);

        // Une autre caisse voit le stock disponible réduit et ne peut pas vendre plus.
        String autre = caissierSur(autreCaisseId);
        ouvrirSession(autre, autreCaisseId);
        valider(autre, UUID.randomUUID().toString(), panier(ligne(produit, 2)), "100")
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/ventes/" + venteId + "/reprise").header(HttpHeaders.AUTHORIZATION, bearer(autre)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ventes/attente").header(HttpHeaders.AUTHORIZATION, bearer(autre)))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/v1/ventes/attente").header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(jsonPath("$[0].noteAttente").value("Parti au DAB"))
                .andExpect(jsonPath("$[0].lignes[0].remise.valeur").value(5));
        mvc.perform(post("/api/v1/ventes/" + venteId + "/reprise").header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[0].quantite").value(2));

        // Le prix change entre-temps : la vente reprise garde son prix.
        jdbc.update("update pos.produit set ppv = 50 where id = ?", produit);
        Map<String, Object> repris = new HashMap<>(panier);
        repris.put("venteEnAttenteId", venteId);
        valider(caissier, UUID.randomUUID().toString(), repris, "80")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(venteId))
                .andExpect(jsonPath("$.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.totaux.totalTtc").value(76.00))
                .andExpect(jsonPath("$.paiement.rendu").value(4.00));
        assertThat(stock(produit)).isEqualTo(1);
        assertThat(stockReserve(produit)).isZero();
    }

    @Test
    void attente_abandonnee_ou_expiree_libere_le_stock() throws Exception {
        String caissier = caissierSur(caisseId);
        ouvrirSession(caissier, caisseId);
        long produit = produit("10.00", 4);

        long abandonnee = mettreEnAttente(caissier, panier(ligne(produit, 1)));
        mvc.perform(post("/api/v1/ventes/" + abandonnee + "/abandon")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(jsonPath("$.statut").value("ANNULEE"));
        assertThat(stockReserve(produit)).isZero();

        long expiree = mettreEnAttente(caissier, panier(ligne(produit, 3)));
        assertThat(stockReserve(produit)).isEqualTo(3);
        jdbc.update(
                "update pos.vente set attente_expire_le = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
                expiree);
        ventesExpirees.annuler();

        assertThat(stockReserve(produit)).isZero();
        assertThat(stock(produit)).isEqualTo(4);
        mvc.perform(post("/api/v1/ventes/" + expiree + "/reprise").header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(status().isConflict());
    }

    @Test
    void cloture_de_session_annule_les_attentes_et_calcule_l_ecart() throws Exception {
        String caissier = caissierSur(caisseId);
        long sessionId = ouvrirSession(caissier, caisseId);
        long produit = produit("30.00", 4);
        valider(caissier, UUID.randomUUID().toString(), panier(ligne(produit, 1)), "50")
                .andExpect(status().isCreated());
        mettreEnAttente(caissier, panier(ligne(produit, 2)));
        assertThat(stockReserve(produit)).isEqualTo(2);

        mvc.perform(post("/api/v1/sessions/" + sessionId + "/cloture")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissierSur(caisseId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantCompte\": 0}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/cloture")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantCompte\": 228.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especesEncaissees").value(30.00))
                .andExpect(jsonPath("$.ecart").value(-1.50));

        assertThat(stockReserve(produit)).isZero();
        assertThat(stock(produit)).isEqualTo(3);
        mvc.perform(get("/api/v1/sessions/courante").header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(status().isNoContent());
    }

    @Test
    void sessions_une_par_caisse_et_par_caissier_sur_une_caisse_autorisee() throws Exception {
        String caissier = caissierSur(caisseId);
        mvc.perform(post("/api/v1/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("caisseId", autreCaisseId, "fondInitial", 0))))
                .andExpect(status().isForbidden());
        ouvrirSession(caissier, caisseId);
        mvc.perform(get("/api/v1/sessions/courante").header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(jsonPath("$.caisseId").value(caisseId))
                .andExpect(jsonPath("$.fondInitial").value(200.00));

        String collegue = caissierSur(caisseId);
        mvc.perform(post("/api/v1/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(collegue))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("caisseId", caisseId, "fondInitial", 0))))
                .andExpect(status().isConflict());
    }

    @Test
    void remise_au_dela_du_plafond_exige_le_pin_d_un_responsable() throws Exception {
        String caissier = caissierSur(caisseId);
        ouvrirSession(caissier, caisseId);
        long produit = produit("100.00", 5);
        Utilisateur responsable = creerUtilisateur(Role.RESPONSABLE);
        responsable.definirPin(encoder.encode(PIN));
        utilisateurs.save(responsable);

        Map<String, Object> panier = panier(remise(ligne(produit, 1), "POURCENT", "15"));
        mvc.perform(post("/api/v1/ventes/calcul")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(panier)))
                .andExpect(jsonPath("$.autorisationRequise").value("RESPONSABLE"))
                .andExpect(jsonPath("$.autorisationAcquise").value(false));
        panier.put("motifRemise", "Client fidèle");
        valider(caissier, UUID.randomUUID().toString(), panier, "85").andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/autorisations/autorisateurs").header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(jsonPath("$[?(@.id == " + responsable.getId() + ")].role")
                        .value("RESPONSABLE"));
        autoriser(caissier, responsable.getId(), "0000")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("PIN incorrect."));
        String jeton = lire(autoriser(caissier, responsable.getId(), PIN)
                        .andExpect(status().isOk())
                        .andReturn())
                .get("jeton")
                .asText();

        // Le jeton d'autorisation n'est pas un jeton d'accès.
        mvc.perform(get("/api/v1/produits").header(HttpHeaders.AUTHORIZATION, bearer(jeton)))
                .andExpect(status().isUnauthorized());
        // Ni utilisable par un autre caissier.
        panier.put("jetonAutorisation", jeton);
        String autre = caissierSur(autreCaisseId);
        ouvrirSession(autre, autreCaisseId);
        valider(autre, UUID.randomUUID().toString(), panier, "85").andExpect(status().isUnprocessableEntity());

        JsonNode vente = lire(valider(caissier, UUID.randomUUID().toString(), panier, "85")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totaux.totalTtc").value(85.00))
                .andExpect(jsonPath("$.valideePar").value(responsable.getId()))
                .andReturn());
        assertThat(journalActions.findAll()).anySatisfy(a -> {
            assertThat(a.getAction()).isEqualTo(ActionJournal.REMISE_AUTORISEE);
            assertThat(a.getDetail()).contains(vente.get("numero").asText(), "Client fidèle");
        });

        // Au-delà du plafond responsable (30 %), le PIN du responsable ne suffit plus.
        panier.put("lignes", List.of(remise(ligne(produit, 1), "POURCENT", "35")));
        valider(caissier, UUID.randomUUID().toString(), panier, "65").andExpect(status().isForbidden());

        // Une vente en attente garde l'autorisation accordée, mais pas pour une remise plus forte.
        panier.put("lignes", List.of(remise(ligne(produit, 1), "POURCENT", "15")));
        long attente = mettreEnAttente(caissier, panier);
        Map<String, Object> reprise = panier(remise(ligne(produit, 1), "POURCENT", "25"));
        reprise.put("motifRemise", "Client fidèle");
        reprise.put("venteEnAttenteId", attente);
        valider(caissier, UUID.randomUUID().toString(), reprise, "75").andExpect(status().isForbidden());
        reprise.put("lignes", List.of(remise(ligne(produit, 1), "POURCENT", "15")));
        valider(caissier, UUID.randomUUID().toString(), reprise, "85")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valideePar").value(responsable.getId()));
    }

    @Test
    void cinq_pin_rates_verrouillent_le_responsable() throws Exception {
        String caissier = caissierSur(caisseId);
        Utilisateur responsable = creerUtilisateur(Role.RESPONSABLE);
        responsable.definirPin(encoder.encode(PIN));
        utilisateurs.save(responsable);
        for (int i = 0; i < Utilisateur.ESSAIS_MAX; i++) {
            autoriser(caissier, responsable.getId(), "9999").andExpect(status().isUnprocessableEntity());
        }
        autoriser(caissier, responsable.getId(), PIN).andExpect(status().isForbidden());
        assertThat(utilisateurs.findById(responsable.getId()).orElseThrow().isVerrouille())
                .isTrue();
    }

    @Test
    void ventes_concurrentes_ne_rendent_jamais_le_stock_negatif() throws Exception {
        long produit = produit("5.00", 3);
        List<String> caissiers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            long caisse = creer("/api/v1/caisses", Map.of("nom", "Concurrence " + i + " " + suffixe, "active", true));
            String jeton = caissierSur(caisse);
            ouvrirSession(jeton, caisse);
            caissiers.add(jeton);
        }

        ExecutorService pool = Executors.newFixedThreadPool(caissiers.size());
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<Integer>> resultats = new ArrayList<>();
        for (String caissier : caissiers) {
            Callable<Integer> vente = () -> {
                depart.await();
                return valider(caissier, UUID.randomUUID().toString(), panier(ligne(produit, 1)), "5")
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            resultats.add(pool.submit(vente));
        }
        depart.countDown();
        List<Integer> statuts = new ArrayList<>();
        for (Future<Integer> resultat : resultats) statuts.add(resultat.get());
        pool.shutdown();

        assertThat(statuts).filteredOn(s -> s == 201).hasSize(3);
        assertThat(statuts).filteredOn(s -> s == 422).hasSize(3);
        assertThat(stock(produit)).isZero();
        assertThat(mouvements.findByProduitIdOrderByIdAsc(produit)).hasSize(3);
    }

    // ---------------------------------------------------------------- Aides

    private ResultActions valider(String jeton, String cle, Map<String, Object> panier, String recu) throws Exception {
        return mvc.perform(post("/api/v1/ventes")
                .header(HttpHeaders.AUTHORIZATION, bearer(jeton))
                .header("Idempotency-Key", cle)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("panier", panier, "especesRecues", new BigDecimal(recu)))));
    }

    private long mettreEnAttente(String jeton, Map<String, Object> panier) throws Exception {
        return lire(mvc.perform(post("/api/v1/ventes/attente")
                                .header(HttpHeaders.AUTHORIZATION, bearer(jeton))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("panier", panier))))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asLong();
    }

    private ResultActions autoriser(String jeton, long autorisateurId, String pin) throws Exception {
        return mvc.perform(post("/api/v1/autorisations/remise")
                .header(HttpHeaders.AUTHORIZATION, bearer(jeton))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("autorisateurId", autorisateurId, "pin", pin))));
    }

    private long ouvrirSession(String jeton, long caisse) throws Exception {
        return lire(mvc.perform(post("/api/v1/sessions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(jeton))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("caisseId", caisse, "fondInitial", 200))))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asLong();
    }

    private String caissierSur(long caisse) throws Exception {
        Utilisateur caissier = creerUtilisateur(Role.CAISSIER);
        caissier.modifierProfil(caissier.getNom(), caissier.getPrenom(), Role.CAISSIER, true, Set.of(caisse));
        utilisateurs.save(caissier);
        return jeton(caissier.getLogin(), MDP_UTILISATEUR);
    }

    private long produit(String ppv, int stock) throws Exception {
        String pid = "V" + UUID.randomUUID().toString().substring(0, 10);
        Map<String, Object> commande = new HashMap<>();
        commande.put("pid", pid);
        commande.put("designation", "PRODUIT " + pid);
        commande.put("categorieId", categorieId);
        commande.put("zoneId", zoneId);
        commande.put("tvaId", tvaId);
        commande.put("ppv", new BigDecimal(ppv));
        commande.put("actif", true);
        commande.put("codesBarres", List.of());
        long id = creer("/api/v1/produits", commande);
        // Le stock ne change que par des mouvements (import, réception) : fixé directement pour le test.
        jdbc.update("update pos.produit set stock = ? where id = ?", stock, id);
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

    private int stock(long produit) {
        return jdbc.queryForObject("select stock from pos.produit where id = ?", Integer.class, produit);
    }

    private int stockReserve(long produit) {
        return jdbc.queryForObject("select stock_reserve from pos.produit where id = ?", Integer.class, produit);
    }

    private static Map<String, Object> ligne(long produit, int quantite) {
        Map<String, Object> ligne = new HashMap<>();
        ligne.put("produitId", produit);
        ligne.put("quantite", quantite);
        return ligne;
    }

    private static Map<String, Object> remise(Map<String, Object> ligne, String type, String valeur) {
        ligne.put("remise", Map.of("type", type, "valeur", new BigDecimal(valeur)));
        return ligne;
    }

    @SafeVarargs
    private static Map<String, Object> panier(Map<String, Object>... lignes) {
        Map<String, Object> panier = new HashMap<>();
        panier.put("lignes", List.of(lignes));
        return panier;
    }
}
