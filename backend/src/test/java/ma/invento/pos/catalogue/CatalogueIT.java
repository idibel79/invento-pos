package ma.invento.pos.catalogue;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.invento.pos.IntegrationTest;
import ma.invento.pos.catalogue.domain.Tva;
import ma.invento.pos.catalogue.infrastructure.TvaRepository;
import ma.invento.pos.identite.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class CatalogueIT extends IntegrationTest {

    @Autowired
    TvaRepository tvaRepository;

    private String admin;
    private String caissier;
    private String suffixe;
    private long categorieId;
    private long zoneId;
    private long tvaId;

    @BeforeEach
    void preparer() throws Exception {
        admin = jetonAdmin();
        caissier = jeton(Role.CAISSIER);
        suffixe = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        categorieId = creerReference("/api/v1/categories", "Catégorie " + suffixe);
        zoneId = creerReference("/api/v1/zones", "Zone " + suffixe);
        tvaId = tvaAVingtPourCent();
    }

    @Test
    void cree_puis_retrouve_un_produit_par_recherche_et_par_scan() throws Exception {
        JsonNode cree = creerProduit("P" + suffixe, "SIROP TOUX " + suffixe, List.of("CB" + suffixe));

        mvc.perform(get("/api/v1/produits")
                        .param("recherche", "sirop toux " + suffixe.toLowerCase())
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu", hasSize(1)))
                .andExpect(jsonPath("$.contenu[0].pid").value("P" + suffixe))
                .andExpect(jsonPath("$.contenu[0].enRupture").value(true))
                .andExpect(jsonPath("$.contenu[0].pph").value(nullValue()))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/v1/produits")
                        .param("recherche", "CB" + suffixe)
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(jsonPath("$.contenu[0].id").value(cree.get("id").asLong()));

        mvc.perform(get("/api/v1/produits/code-barre/CB" + suffixe).header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designation").value("SIROP TOUX " + suffixe))
                .andExpect(jsonPath("$.tva.taux").value(20.0));

        mvc.perform(get("/api/v1/produits/" + cree.get("id").asLong()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.pph").value(6.0));
    }

    @Test
    void filtre_par_zone_categorie_et_disponibilite() throws Exception {
        creerProduit("Q" + suffixe, "PRODUIT FILTRE " + suffixe, List.of());

        mvc.perform(get("/api/v1/produits")
                        .param("zoneId", String.valueOf(zoneId))
                        .param("categorieId", String.valueOf(categorieId))
                        .param("disponibilite", "RUPTURE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(jsonPath("$.contenu", hasSize(1)));
        mvc.perform(get("/api/v1/produits")
                        .param("zoneId", String.valueOf(zoneId))
                        .param("disponibilite", "EN_STOCK")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(jsonPath("$.contenu", hasSize(0)));
    }

    @Test
    void recherche_insensible_aux_caracteres_speciaux_de_like() throws Exception {
        creerProduit("R" + suffixe, "CREME 100% " + suffixe, List.of());
        mvc.perform(get("/api/v1/produits")
                        .param("recherche", "%")
                        .param("zoneId", String.valueOf(zoneId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(jsonPath("$.contenu", hasSize(1)));
        mvc.perform(get("/api/v1/produits")
                        .param("recherche", "_")
                        .param("zoneId", String.valueOf(zoneId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(jsonPath("$.contenu", hasSize(0)));
    }

    @Test
    void modification_avec_verrouillage_optimiste_et_codes_barres_uniques() throws Exception {
        JsonNode premier = creerProduit("S" + suffixe, "PREMIER " + suffixe, List.of("S1" + suffixe));
        JsonNode second = creerProduit("T" + suffixe, "SECOND " + suffixe, List.of());
        long id = second.get("id").asLong();

        Map<String, Object> modification =
                commande("T" + suffixe, "SECOND MODIFIE " + suffixe, List.of("S1" + suffixe));
        modification.put("version", second.get("version").asLong());
        mvc.perform(put("/api/v1/produits/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(modification)))
                .andExpect(status().isConflict());

        modification.put("codesBarres", List.of("T1" + suffixe));
        mvc.perform(put("/api/v1/produits/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(modification)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designation").value("SECOND MODIFIE " + suffixe));

        // Version périmée : quelqu'un a modifié le produit entre-temps.
        mvc.perform(put("/api/v1/produits/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(modification)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/produits")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(commande("S" + suffixe, "DOUBLON", List.of()))))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/produits/" + premier.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.codesBarres[0]").value("S1" + suffixe));
    }

    @Test
    void ecriture_reservee_a_l_admin_et_erreurs_au_format_problem_detail() throws Exception {
        mvc.perform(post("/api/v1/produits")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(commande("U" + suffixe, "INTERDIT", List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));

        mvc.perform(post("/api/v1/produits")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pid\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs.pid").exists());

        mvc.perform(get("/api/v1/produits/code-barre/INCONNU" + suffixe)
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Aucun produit pour ce code-barre."))
                .andExpect(header().exists("X-Request-Id"));

        mvc.perform(get("/api/v1/produits")
                        .param("sort", "inexistant")
                        .header(HttpHeaders.AUTHORIZATION, bearer(caissier)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/produits")).andExpect(status().isUnauthorized());
    }

    private JsonNode creerProduit(String pid, String designation, List<String> codes) throws Exception {
        return lire(mvc.perform(post("/api/v1/produits")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(commande(pid, designation, codes))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private Map<String, Object> commande(String pid, String designation, List<String> codes) {
        Map<String, Object> commande = new HashMap<>();
        commande.put("pid", pid);
        commande.put("designation", designation);
        commande.put("categorieId", categorieId);
        commande.put("zoneId", zoneId);
        commande.put("tvaId", tvaId);
        commande.put("ppv", new BigDecimal("9.60"));
        commande.put("pph", new BigDecimal("6.00"));
        commande.put("actif", true);
        commande.put("codesBarres", codes);
        return commande;
    }

    private long creerReference(String chemin, String nom) throws Exception {
        return lire(mvc.perform(post(chemin)
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("nom", nom))))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asLong();
    }

    /** Les TVA viennent de l'import ; on en crée une à 20 % si la base est vide. */
    private long tvaAVingtPourCent() throws Exception {
        JsonNode tvas = lire(mvc.perform(get("/api/v1/tvas").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andReturn());
        for (JsonNode tva : tvas) {
            if (tva.get("taux").decimalValue().compareTo(BigDecimal.valueOf(20)) == 0)
                return tva.get("id").asLong();
        }
        return tvaRepository.save(new Tva("TVA", new BigDecimal("20.00"))).getId();
    }
}
