package ma.invento.pos.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ma.invento.pos.IntegrationTest;
import ma.invento.pos.catalogue.domain.Produit;
import ma.invento.pos.catalogue.infrastructure.CategorieRepository;
import ma.invento.pos.catalogue.infrastructure.ProduitRepository;
import ma.invento.pos.catalogue.infrastructure.TvaRepository;
import ma.invento.pos.catalogue.infrastructure.ZoneRepository;
import ma.invento.pos.identite.domain.Role;
import ma.invento.pos.importation.domain.LecteurLigne;
import ma.invento.pos.journal.domain.ActionJournal;
import ma.invento.pos.journal.infrastructure.JournalActionRepository;
import ma.invento.pos.stock.domain.TypeMouvement;
import ma.invento.pos.stock.infrastructure.MouvementStockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

/** Import du vrai export Sobrus du 02/10/2026 (cadrage § 2). */
class ImportSobrusIT extends IntegrationTest {

    private static final String FICHIER = "/import/sobrus-stocks-2026-10-02.xlsx";
    private static final int PRODUITS = 2147;

    @Autowired
    ProduitRepository produits;

    @Autowired
    CategorieRepository categories;

    @Autowired
    ZoneRepository zones;

    @Autowired
    TvaRepository tvas;

    @Autowired
    MouvementStockRepository mouvements;

    @Autowired
    JournalActionRepository journal;

    @Autowired
    TransactionTemplate transaction;

    private static MockMultipartFile fichier() throws IOException {
        try (InputStream contenu = ImportSobrusIT.class.getResourceAsStream(FICHIER)) {
            return new MockMultipartFile("fichier", "Sobrus -Stocks - 2026-10-02.xlsx", null, contenu.readAllBytes());
        }
    }

    @Test
    void importe_le_catalogue_sobrus_puis_le_reimporte_sans_effet() throws Exception {
        String admin = jetonAdmin();

        JsonNode analyse = lire(mvc.perform(multipart("/api/v1/imports/catalogue/analyse")
                        .file(fichier())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("SOBRUS"))
                .andReturn());
        assertThat(analyse.get("lignesLues").asInt()).isEqualTo(PRODUITS);
        assertThat(analyse.get("produitsCrees").asInt()).isZero();
        assertThat(produits.findAll()).noneMatch(p -> p.getPid().equals("27000"));

        JsonNode rapport = lire(mvc.perform(multipart("/api/v1/imports/catalogue")
                        .file(fichier())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(rapport.get("nombreErreurs").asInt()).isZero();
        assertThat(rapport.get("nombreAvertissements").asInt()).isEqualTo(3);
        assertThat(rapport.get("produitsCrees").asInt()).isEqualTo(PRODUITS);
        assertThat(rapport.get("produitsEnStockNegatif").asInt()).isEqualTo(36);
        // 60 produits sans code-barre 1, dont un qui a un code-barre 2 : 59 vendables par recherche uniquement.
        assertThat(rapport.get("produitsSansCodeBarre").asInt()).isEqualTo(59);

        transaction.executeWithoutResult(tx -> {
            Produit vigorex = produits.findAllBy().stream()
                    .filter(p -> p.getPid().equals("27000"))
                    .findFirst()
                    .orElseThrow();
            assertThat(vigorex.getDesignation()).isEqualTo("VIGOREX CO 50MG B1 COMP");
            assertThat(vigorex.getCategorie().getNom()).isEqualTo("Médicament");
            assertThat(vigorex.getTauxMarge()).isEqualByComparingTo("33.930");
            assertThat(vigorex.getTva().getLibelle()).isEqualTo("Exonéré");
            assertThat(vigorex.getZone().getNom()).isEqualTo("Comptoir");
            assertThat(vigorex.getPpv()).isEqualByComparingTo("25.20");
            assertThat(vigorex.getPph()).isEqualByComparingTo("16.65");
            assertThat(vigorex.getStock()).isEqualTo(4);
            assertThat(vigorex.getCodesBarres()).containsExactly("6118000050643");

            // Les autres tests créent leurs propres produits (PID non numériques) : on les écarte.
            List<Produit> sobrus = produits.findAllBy().stream()
                    .filter(p -> p.getPid().matches("\\d+"))
                    .toList();
            assertThat(sobrus).hasSize(PRODUITS);
            Set<String> codes = new HashSet<>();
            sobrus.forEach(p -> codes.addAll(p.getCodesBarres()));
            // 2 684 codes lus : 19 produits répètent leur code 1 en code 2, et 3 codes sont partagés par
            // deux produits (6118002100056, 2017022048, 796554055404) : le premier garde le code.
            assertThat(codes).hasSize(2087 + 597 - 19 - 3);

            // 33 zones nommées + « Non assigné » pour les 116 produits sans zone, espaces de fin retirés.
            Set<String> zonesSobrus = new HashSet<>();
            sobrus.forEach(p -> zonesSobrus.add(p.getZone().getNom()));
            assertThat(zonesSobrus).hasSize(34).contains(LecteurLigne.ZONE_NON_ASSIGNEE);
            assertThat(sobrus.stream().filter(p -> p.getZone().getNom().equals(LecteurLigne.ZONE_NON_ASSIGNEE)))
                    .hasSize(116);
        });

        assertThat(tvas.findAll()).extracting(t -> t.getTaux().intValue()).containsExactlyInAnyOrder(0, 20);
        assertThat(categories.findAll())
                .extracting(c -> c.getNom())
                .contains("Médicament", "Parapharmacie", "Diététique")
                .doesNotHaveDuplicates();
        assertThat(zones.findAll()).noneMatch(z -> z.getNom().endsWith(" "));
        assertThat(mouvements.findAll()).allMatch(m -> m.getType() == TypeMouvement.IMPORT);
        assertThat(journal.findByAction(ActionJournal.IMPORT_CATALOGUE)).isNotEmpty();

        // Le scan d'un code-barre importé retrouve le produit.
        mvc.perform(get("/api/v1/produits/code-barre/6118000050643").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pid").value("27000"));

        long mouvementsAvant = mouvements.count();
        JsonNode reimport = lire(mvc.perform(multipart("/api/v1/imports/catalogue")
                        .file(fichier())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(reimport.get("produitsCrees").asInt()).isZero();
        assertThat(reimport.get("produitsMisAJour").asInt()).isEqualTo(PRODUITS);
        assertThat(reimport.get("mouvementsStock").asInt()).isZero();
        assertThat(mouvements.count()).isEqualTo(mouvementsAvant);
    }

    @Test
    void import_reserve_a_l_admin() throws Exception {
        mvc.perform(multipart("/api/v1/imports/catalogue")
                        .file(fichier())
                        .header(HttpHeaders.AUTHORIZATION, bearer(jeton(Role.RESPONSABLE))))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/imports/catalogue").file(fichier())).andExpect(status().isUnauthorized());
    }

    @Test
    void fichier_non_excel_refuse_proprement() throws Exception {
        mvc.perform(multipart("/api/v1/imports/catalogue/analyse")
                        .file(new MockMultipartFile("fichier", "notes.txt", null, "pas un classeur".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(jetonAdmin())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Fichier Excel illisible."));
    }
}
